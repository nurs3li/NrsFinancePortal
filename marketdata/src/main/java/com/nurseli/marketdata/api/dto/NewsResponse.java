package com.nurseli.marketdata.api.dto;

import com.nurseli.marketdata.domain.news.NewsCategory;

import java.time.LocalDateTime;

public record NewsResponse(
        Long id,
        String title,
        String summary,
        String source,
        String url,
        NewsCategory category,
        LocalDateTime publishedAt,
        LocalDateTime createdAt
) {}