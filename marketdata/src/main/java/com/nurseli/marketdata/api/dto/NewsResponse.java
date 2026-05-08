package com.nurseli.marketdata.api.dto;

import com.nurseli.marketdata.domain.news.NewsCategory;

import java.time.LocalDateTime;

public record NewsResponse(
        Long id,
        String title,
        String titleTr,
        String summary,
        String contentTr,
        String source,
        String url,
        NewsCategory category,
        LocalDateTime publishedAt,
        LocalDateTime createdAt
) {}