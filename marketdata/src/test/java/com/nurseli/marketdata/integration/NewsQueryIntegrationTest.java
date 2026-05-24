package com.nurseli.marketdata.integration;

import com.nurseli.marketdata.domain.news.NewsCategory;
import com.nurseli.marketdata.infrastructure.persistence.NewsRepository;
import com.nurseli.marketdata.integration.support.MarketdataIntegrationFixtures;
import com.nurseli.marketdata.integration.support.MarketdataIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NewsQueryIntegrationTest extends MarketdataIntegrationTestBase {

    @Autowired
    private NewsRepository newsRepository;

    private long stockNewsId;

    @BeforeEach
    void seedNews() {
        stockNewsId = newsRepository.save(
                MarketdataIntegrationFixtures.news("it-stock-1", "BIST rally", NewsCategory.STOCK)
        ).getId();
        newsRepository.save(
                MarketdataIntegrationFixtures.news("it-general-1", "Macro outlook", NewsCategory.GENERAL)
        );
    }

    @Test
    void listNews_returnsPagedArticles() throws Exception {
        mockMvc.perform(get("/api/news").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].title").exists());
    }

    @Test
    void listNewsByCategory_filtersResults() throws Exception {
        mockMvc.perform(get("/api/news").param("category", "STOCK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].category").value("STOCK"));
    }

    @Test
    void getNewsById_returnsDetail() throws Exception {
        mockMvc.perform(get("/api/news/{id}", stockNewsId).header("Accept-Language", "tr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(stockNewsId))
                .andExpect(jsonPath("$.data.title").value("BIST rally"));
    }
}
