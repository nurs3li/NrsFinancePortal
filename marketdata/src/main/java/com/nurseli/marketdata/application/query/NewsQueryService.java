package com.nurseli.marketdata.application.query;

import com.nurseli.marketdata.application.TranslationService;
import com.nurseli.marketdata.api.dto.NewsResponse;
import com.nurseli.marketdata.domain.news.News;
import com.nurseli.marketdata.domain.news.NewsCategory;
import com.nurseli.marketdata.infrastructure.persistence.NewsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
public class NewsQueryService {

    private final NewsRepository newsRepository;
    private final TranslationService translationService;
    @Qualifier("newsReadExecutor")
    private final Executor newsReadExecutor;

    public Page<NewsResponse> getAllNews(Pageable pageable, String lang, boolean detail) {
        Page<News> page = newsRepository.findAllByOrderByPublishedAtDesc(pageable);
        return mapPage(page, lang, detail);
    }

    public Page<NewsResponse> getNewsByCategory(NewsCategory category, Pageable pageable, String lang, boolean detail) {
        Page<News> page = newsRepository.findByCategoryOrderByPublishedAtDesc(category, pageable);
        return mapPage(page, lang, detail);
    }

    @Transactional
    public NewsResponse getNewsById(Long id, String lang) {
        News news = newsRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("News not found with id: " + id));
        persistTurkishFieldsIfMissing(news, lang);
        return toResponse(news, lang, true);
    }

    /**
     * İlk detay açılışında çeviriyi DB'ye yazar; sonraki isteklerde ağ çağrısı olmaz.
     */
    private void persistTurkishFieldsIfMissing(News news, String lang) {
        if (!isTurkish(lang) || !translationService.isEnabled()) {
            return;
        }
        boolean dirty = false;
        if (isBlank(news.getTitleTr()) && !isBlank(news.getTitle())) {
            String tr = translationService.translate(news.getTitle());
            if (!isBlank(tr)) {
                news.setTitleTr(tr);
                dirty = true;
            }
        }
        if (isBlank(news.getSummaryTr()) && !isBlank(news.getSummary())) {
            String tr = translationService.translate(news.getSummary());
            if (!isBlank(tr)) {
                news.setSummaryTr(tr);
                dirty = true;
            }
        }
        if (dirty) {
            newsRepository.save(news);
        }
    }

    private Page<NewsResponse> mapPage(Page<News> page, String lang, boolean detail) {
        if (!isTurkish(lang)) {
            return page.map(news -> toResponse(news, lang, detail));
        }
        List<News> content = page.getContent();
        if (content.isEmpty()) {
            return new PageImpl<>(List.of(), page.getPageable(), page.getTotalElements());
        }
        List<CompletableFuture<NewsResponse>> futures = new ArrayList<>(content.size());
        for (News n : content) {
            futures.add(CompletableFuture.supplyAsync(() -> toResponse(n, lang, detail), newsReadExecutor));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        List<NewsResponse> mapped = futures.stream().map(CompletableFuture::join).toList();
        return new PageImpl<>(mapped, page.getPageable(), page.getTotalElements());
    }

    private NewsResponse toResponse(News news, String lang, boolean detail) {
        String titleTr = null;
        String contentTr = null;
        if (isTurkish(lang)) {
            titleTr = firstNonBlank(news.getTitleTr(), () -> translationService.translate(news.getTitle()));
            if (detail) {
                contentTr = firstNonBlank(news.getSummaryTr(), () -> translationService.translate(news.getSummary()));
            }
        }
        return new NewsResponse(
                news.getId(),
                news.getTitle(),
                titleTr,
                news.getSummary(),
                contentTr,
                news.getSource(),
                news.getUrl(),
                news.getCategory(),
                news.getPublishedAt(),
                news.getCreatedAt()
        );
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean isTurkish(String lang) {
        return lang != null && lang.toLowerCase().startsWith("tr");
    }

    private static String firstNonBlank(String direct, java.util.function.Supplier<String> fallback) {
        if (!isBlank(direct)) {
            return direct;
        }
        String fb = fallback.get();
        return isBlank(fb) ? null : fb;
    }
}
