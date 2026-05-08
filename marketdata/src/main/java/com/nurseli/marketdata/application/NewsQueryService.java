package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.NewsResponse;
import com.nurseli.marketdata.domain.news.News;
import com.nurseli.marketdata.domain.news.NewsCategory;
import com.nurseli.marketdata.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NewsQueryService {

    private final NewsRepository newsRepository;
    private final TranslationService translationService;

    public Page<NewsResponse> getAllNews(Pageable pageable, String lang, boolean detail) {
        return newsRepository.findAllByOrderByPublishedAtDesc(pageable)
                .map(news -> toResponse(news, lang, detail));
    }

    public Page<NewsResponse> getNewsByCategory(NewsCategory category, Pageable pageable, String lang, boolean detail) {
        return newsRepository.findByCategoryOrderByPublishedAtDesc(category, pageable)
                .map(news -> toResponse(news, lang, detail));
    }

    public NewsResponse getNewsById(Long id, String lang) {
        News news = newsRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("News not found with id: " + id));
        return toResponse(news, lang, true);
    }

    private NewsResponse toResponse(News news, String lang, boolean detail) {
        String titleTr = null;
        String contentTr = null;
        if (lang != null && lang.toLowerCase().startsWith("tr")) {
            titleTr = translationService.translate(news.getTitle());
            if (detail) {
                contentTr = translationService.translate(news.getSummary());
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
}