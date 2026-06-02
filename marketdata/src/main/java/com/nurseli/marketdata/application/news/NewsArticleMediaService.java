package com.nurseli.marketdata.application.news;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.marketdata.api.dto.NewsArticleImageDto;
import com.nurseli.marketdata.domain.news.News;
import com.nurseli.marketdata.infrastructure.persistence.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class NewsArticleMediaService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(45);
    private static final int MAX_IMAGES = 8;
    private static final String CACHE_KEY_PREFIX = "news:media:v3:";

    private final NewsRepository newsRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public List<NewsArticleImageDto> getArticleImages(Long newsId) {
        News news = newsRepository.findById(newsId)
                .orElseThrow(() -> new IllegalStateException("News not found with id: " + newsId));
        if (!StringUtils.hasText(news.getUrl())) {
            return List.of();
        }

        String cacheKey = cacheKey(newsId);
        List<NewsArticleImageDto> cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<NewsArticleImageDto> images = fetchImages(news);
        writeCache(cacheKey, images);
        return images;
    }

    private List<NewsArticleImageDto> fetchImages(News news) {
        String pageUrl = news.getUrl().trim();
        if (!isSafePublicHttpUrl(pageUrl)) {
            return List.of();
        }

        try {
            Document doc = Jsoup.connect(pageUrl)
                    .userAgent("Mozilla/5.0 (compatible; NrsFinancePortal/1.0; +https://nrs.local)")
                    .referrer("https://www.google.com/")
                    .timeout(12_000)
                    .followRedirects(true)
                    .get();

            String fallbackCaption = firstNonBlank(
                    metaContent(doc, "property", "og:description"),
                    metaContent(doc, "name", "description"),
                    news.getSummary(),
                    news.getTitle()
            );

            if (isHeroOnlySource(pageUrl)) {
                return extractHeroOnlyImages(doc, pageUrl, fallbackCaption);
            }

            Map<String, NewsArticleImageDto> deduped = new LinkedHashMap<>();

            addImage(deduped, metaContent(doc, "property", "og:image"), firstNonBlank(
                    metaContent(doc, "property", "og:title"),
                    fallbackCaption
            ), pageUrl);

            addImage(deduped, metaContent(doc, "name", "twitter:image"), firstNonBlank(
                    metaContent(doc, "name", "twitter:title"),
                    fallbackCaption
            ), pageUrl);

            Elements figures = doc.select("article figure, main figure, figure");
            for (Element figure : figures) {
                Element img = figure.selectFirst("img[src]");
                if (img == null) {
                    continue;
                }
                String caption = firstNonBlank(
                        figure.selectFirst("figcaption") != null ? figure.selectFirst("figcaption").text() : null,
                        img.attr("alt"),
                        img.attr("title"),
                        fallbackCaption
                );
                addImage(deduped, img.attr("src"), caption, pageUrl);
            }

            Elements imgs = doc.select("article img[src], main img[src], .content img[src], .post-content img[src]");
            for (Element img : imgs) {
                if (deduped.size() >= MAX_IMAGES) {
                    break;
                }
                String caption = firstNonBlank(img.attr("alt"), img.attr("title"), fallbackCaption);
                addImage(deduped, img.attr("src"), caption, pageUrl);
            }

            return deduped.values().stream().limit(MAX_IMAGES).toList();
        } catch (Exception ex) {
            log.debug("[NEWS_MEDIA] fetch_failed newsId={} url={} reason={}", news.getId(), pageUrl, ex.getMessage());
            return List.of();
        }
    }

    /** Kapak görseli yeterli olan kaynaklar (sayfa içi img taraması yapılmaz). */
    private static List<NewsArticleImageDto> extractHeroOnlyImages(Document doc, String pageUrl, String fallbackCaption) {
        String caption = firstNonBlank(
                metaContent(doc, "property", "og:title"),
                metaContent(doc, "name", "twitter:title"),
                fallbackCaption
        );

        NewsArticleImageDto hero = buildImage(
                metaContent(doc, "property", "og:image"),
                caption,
                pageUrl
        );
        if (hero != null) {
            return List.of(hero);
        }

        hero = buildImage(metaContent(doc, "name", "twitter:image"), caption, pageUrl);
        if (hero != null) {
            return List.of(hero);
        }

        Element leadImg = doc.selectFirst(
                "article header img[src], article .article-hero img[src], article figure img[src], article img[src]"
        );
        if (leadImg != null) {
            String leadCaption = firstNonBlank(leadImg.attr("alt"), leadImg.attr("title"), caption);
            hero = buildImage(leadImg.attr("src"), leadCaption, pageUrl);
            if (hero != null) {
                return List.of(hero);
            }
        }

        return List.of();
    }

    private static NewsArticleImageDto buildImage(String rawSrc, String caption, String pageUrl) {
        if (!StringUtils.hasText(rawSrc)) {
            return null;
        }
        String src = rawSrc.trim();
        if (src.startsWith("data:")) {
            return null;
        }
        String absolute = toAbsoluteUrl(pageUrl, src);
        if (!StringUtils.hasText(absolute) || !isSafePublicHttpUrl(absolute) || looksLikeIcon(absolute)) {
            return null;
        }
        String cleanCaption = StringUtils.hasText(caption) ? caption.trim() : null;
        return new NewsArticleImageDto(absolute, cleanCaption);
    }

    private static boolean isHeroOnlySource(String pageUrl) {
        try {
            String host = URI.create(pageUrl.trim()).getHost();
            if (!StringUtils.hasText(host)) {
                return false;
            }
            String lower = host.toLowerCase(Locale.ROOT);
            return lower.equals("coindesk.com")
                    || lower.endsWith(".coindesk.com")
                    || lower.equals("cointelegraph.com")
                    || lower.endsWith(".cointelegraph.com")
                    || lower.equals("cryptocurrencynews.com")
                    || lower.endsWith(".cryptocurrencynews.com");
        } catch (Exception ex) {
            return false;
        }
    }

    private void addImage(Map<String, NewsArticleImageDto> target, String rawSrc, String caption, String pageUrl) {
        if (!StringUtils.hasText(rawSrc)) {
            return;
        }
        String src = rawSrc.trim();
        if (src.startsWith("data:")) {
            return;
        }
        String absolute = toAbsoluteUrl(pageUrl, src);
        if (!StringUtils.hasText(absolute) || !isSafePublicHttpUrl(absolute)) {
            return;
        }
        if (looksLikeIcon(absolute)) {
            return;
        }
        String normalizedKey = absolute.toLowerCase(Locale.ROOT);
        if (target.containsKey(normalizedKey)) {
            return;
        }
        String cleanCaption = StringUtils.hasText(caption) ? caption.trim() : null;
        target.put(normalizedKey, new NewsArticleImageDto(absolute, cleanCaption));
    }

    private static String toAbsoluteUrl(String pageUrl, String src) {
        try {
            URI base = URI.create(pageUrl);
            return base.resolve(src).toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private static boolean looksLikeIcon(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("sprite")
                || lower.contains("favicon")
                || lower.contains("/icon")
                || lower.contains("gravatar.com")
                || lower.contains("brudcrumb")
                || lower.contains("breadcrumb")
                || lower.endsWith(".gif")
                || lower.contains("rt.gif")) {
            return true;
        }
        return lower.endsWith(".svg") || lower.contains("logo");
    }

    private static String metaContent(Document doc, String attr, String value) {
        Element el = doc.selectFirst("meta[" + attr + "=" + value + "]");
        if (el == null) {
            return null;
        }
        return el.attr("content");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v.trim();
            }
        }
        return null;
    }

    private static boolean isSafePublicHttpUrl(String url) {
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return false;
            }
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                return false;
            }
            String lowerHost = host.toLowerCase(Locale.ROOT);
            if (lowerHost.equals("localhost")
                    || lowerHost.endsWith(".local")
                    || lowerHost.equals("127.0.0.1")
                    || lowerHost.equals("0.0.0.0")) {
                return false;
            }
            InetAddress address = InetAddress.getByName(host);
            return !address.isAnyLocalAddress()
                    && !address.isLoopbackAddress()
                    && !address.isLinkLocalAddress()
                    && !address.isSiteLocalAddress();
        } catch (Exception ex) {
            return false;
        }
    }

    private List<NewsArticleImageDto> readCache(String key) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(json)) {
                return null;
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return null;
        }
    }

    private void writeCache(String key, List<NewsArticleImageDto> images) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(images), CACHE_TTL);
        } catch (Exception ex) {
            log.debug("[NEWS_MEDIA] cache_write_failed key={} reason={}", key, ex.getMessage());
        }
    }

    private static String cacheKey(Long newsId) {
        return CACHE_KEY_PREFIX + newsId;
    }
}
