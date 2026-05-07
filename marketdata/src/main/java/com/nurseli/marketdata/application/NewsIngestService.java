package com.nurseli.marketdata.application;

import com.nurseli.marketdata.domain.news.News;
import com.nurseli.marketdata.domain.news.NewsCategory;
import com.nurseli.marketdata.infrastructure.finhub.FinHubClient;
import com.nurseli.marketdata.infrastructure.finhub.FinHubNewsDto;
import com.nurseli.marketdata.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsIngestService {

    private final FinHubClient finHubClient;
    private final NewsRepository newsRepository;

    private static final Map<String, NewsCategory> CATEGORY_MAP = Map.of(
            "general", NewsCategory.GENERAL,
            "forex", NewsCategory.FOREX,
            "crypto", NewsCategory.CRYPTO,
            "stock", NewsCategory.STOCK,
            "merger", NewsCategory.GENERAL
    );

    // NOT: Burada @Transactional yok. Bir item patlarsa tüm session kirlenmesin.
    public void fetchAndSaveNews() {
        List<String> categories = List.of("general", "forex", "crypto", "stock");

        for (String category : categories) {
            try {
                List<FinHubNewsDto.NewsItem> items = finHubClient.fetchNews(category).block();

                if (items == null || items.isEmpty()) {
                    log.warn("[NEWS] No results for category: {}", category);
                    continue;
                }

                NewsCategory feedCategory = CATEGORY_MAP.getOrDefault(category, NewsCategory.GENERAL);
                int savedCount = 0;
                int skippedCount = 0;

                for (FinHubNewsDto.NewsItem item : items) {
                    String externalId = item.getId() != null ? String.valueOf(item.getId()) : null;
                    if (externalId == null || externalId.isBlank()) {
                        skippedCount++;
                        continue;
                    }
                    NewsCategory candidateCategory = classifyCategory(feedCategory, item.getHeadline(), item.getSummary());

                    // Hızlı pre-check (race condition tamamen çözmez ama gereksiz insert'i azaltır)
                    if (newsRepository.existsByExternalId(externalId)) {
                        newsRepository.findByExternalId(externalId).ifPresent(existing -> {
                            if (shouldUpgradeCategory(existing.getCategory(), candidateCategory)) {
                                existing.setCategory(candidateCategory);
                                newsRepository.save(existing);
                            }
                        });
                        skippedCount++;
                        continue;
                    }

                    News news = News.builder()
                            .externalId(externalId)
                            .title(item.getHeadline() != null ? item.getHeadline() : "No title")
                            .summary(item.getSummary())
                            .source(item.getSource() != null ? item.getSource() : "FinHub")
                            .url(item.getUrl())
                            .category(candidateCategory)
                            .publishedAt(parseDateTime(item.getDatetime()))
                            .build();

                    try {
                        newsRepository.saveAndFlush(news);
                        savedCount++;
                    } catch (DataIntegrityViolationException duplicateEx) {
                        // Race condition veya tekrar ingest: duplicate'i yut ve devam et
                        skippedCount++;
                        log.debug("[NEWS] Duplicate externalId skipped: {}", externalId);
                    }
                }

                log.info("[NEWS] Category: {}, Saved: {}, Skipped: {}", category, savedCount, skippedCount);

            } catch (Exception e) {
                log.error("[NEWS] Error fetching news for category {}: {}", category, e.getMessage(), e);
            }
        }
    }

    private NewsCategory classifyCategory(NewsCategory feedCategory, String headline, String summary) {
        String text = ((headline == null ? "" : headline) + " " + (summary == null ? "" : summary))
                .toLowerCase(Locale.ROOT);
        if (text.contains("altın")
                || text.contains("gold")
                || text.contains("xau")
                || text.contains("ons")
                || text.contains("silver")
                || text.contains("gümüş")
                || text.contains("brent")
                || text.contains("crude oil")
                || text.contains("emtia")
                || text.contains("commodity")) {
            return NewsCategory.COMMODITY;
        }
        if (text.contains("etf")
                || text.contains("fund")
                || text.contains("fon")
                || text.contains("mutual fund")
                || text.contains("index fund")
                || text.contains("exchange traded fund")
                || text.contains("vanguard")
                || text.contains("ishares")
                || text.contains("spy")
                || text.contains("qqq")
                || text.contains("voo")
                || text.contains("vti")
                || text.contains("iwm")
                || text.contains("eem")
                || text.contains("vwo")
                || text.contains("gld")) {
            return NewsCategory.FUND;
        }
        if (text.contains("viop")
                || text.contains("vadeli")
                || text.contains("futures")
                || text.contains("open interest")
                || text.contains("open-interest")) {
            return NewsCategory.VIOP;
        }
        if (text.contains("tahvil")
                || text.contains("bond")
                || text.contains("hazine")
                || text.contains("yield")
                || text.contains("coupon")) {
            return NewsCategory.BOND;
        }
        return feedCategory;
    }

    private boolean shouldUpgradeCategory(NewsCategory current, NewsCategory candidate) {
        return rank(candidate) > rank(current);
    }

    private int rank(NewsCategory category) {
        if (category == null) return 0;
        return switch (category) {
            case GENERAL -> 0;
            case FOREX, CRYPTO, STOCK, FUND, BOND, COMMODITY -> 1;
            case VIOP -> 2;
        };
    }

    private LocalDateTime parseDateTime(String datetimeStr) {
        if (datetimeStr == null || datetimeStr.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            long timestamp = Long.parseLong(datetimeStr);
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
        } catch (NumberFormatException e) {
            log.warn("[NEWS] Invalid datetime format: {}", datetimeStr);
            return LocalDateTime.now();
        }
    }
}