package com.nurseli.marketdata.controller;

import com.nurseli.marketdata.api.dto.NewsResponse;
import com.nurseli.marketdata.application.NewsQueryService;
import com.nurseli.marketdata.domain.news.NewsCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsQueryService newsQueryService;

    /**
     * GET /api/news
     * Liste: kategori filtresi ve sayfalama
     */
    @GetMapping
    public Page<NewsResponse> getAllNews(
            @RequestParam(required = false) NewsCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);

        if (category != null) {
            return newsQueryService.getNewsByCategory(category, pageable);
        }

        return newsQueryService.getAllNews(pageable);
    }

    /**
     * GET /api/news/{id}
     * Tek haber detayı
     */
    @GetMapping("/{id}")
    public NewsResponse getNewsById(@PathVariable Long id) {
        return newsQueryService.getNewsById(id);
    }
}