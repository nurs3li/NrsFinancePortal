package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.NewsArticleImageDto;
import com.nurseli.marketdata.api.dto.NewsResponse;
import com.nurseli.marketdata.application.NewsArticleMediaService;
import com.nurseli.marketdata.application.NewsQueryService;
import com.nurseli.marketdata.domain.news.NewsCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/news", "/api/news"})
@RequiredArgsConstructor
public class NewsController {

    private final NewsQueryService newsQueryService;
    private final NewsArticleMediaService newsArticleMediaService;

    /**
     * GET /api/news
     * Liste: kategori filtresi ve sayfalama
     */
    @GetMapping
    public Page<NewsResponse> getAllNews(
            @RequestParam(required = false) NewsCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean detail,
            @RequestHeader(value = "Accept-Language", required = false) String lang
    ) {
        Pageable pageable = PageRequest.of(page, size);

        if (category != null) {
            return newsQueryService.getNewsByCategory(category, pageable, lang, detail);
        }

        return newsQueryService.getAllNews(pageable, lang, detail);
    }

    /**
     * GET /api/news/{id}
     * Tek haber detayı
     */
    @GetMapping("/{id}")
    public NewsResponse getNewsById(
            @PathVariable Long id,
            @RequestHeader(value = "Accept-Language", required = false) String lang
    ) {
        return newsQueryService.getNewsById(id, lang);
    }

    /**
     * GET /api/news/{id}/media
     * Kaynak makale URL'sinden görsel + kısa açıklama çıkarır.
     */
    @GetMapping("/{id}/media")
    public List<NewsArticleImageDto> getNewsMedia(@PathVariable Long id) {
        return newsArticleMediaService.getArticleImages(id);
    }
}