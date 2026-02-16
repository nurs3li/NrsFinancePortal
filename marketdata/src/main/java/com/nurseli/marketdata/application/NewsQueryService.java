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

    public Page<NewsResponse> getAllNews(Pageable pageable) {
        return newsRepository.findAllByOrderByPublishedAtDesc(pageable)
                .map(this::toResponse);
    }

    public Page<NewsResponse> getNewsByCategory(NewsCategory category, Pageable pageable) {
        return newsRepository.findByCategoryOrderByPublishedAtDesc(category, pageable)
                .map(this::toResponse);
    }

    public NewsResponse getNewsById(Long id) {
        News news = newsRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("News not found with id: " + id));
        return toResponse(news);
    }

    private NewsResponse toResponse(News news) {
        return new NewsResponse(
                news.getId(),
                news.getTitle(),
                news.getSummary(),
                news.getSource(),
                news.getUrl(),
                news.getCategory(),
                news.getPublishedAt(),
                news.getCreatedAt()
        );
    }
}