package com.nurseli.marketdata.infrastructure.finhub;

import com.nurseli.marketdata.config.DataSourcesProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Slf4j
public class FinHubClient {

    private final WebClient webClient;
    private final DataSourcesProperties dataSourcesProperties;

    public FinHubClient(DataSourcesProperties dataSourcesProperties) {
        this.dataSourcesProperties = dataSourcesProperties;
        String baseUrl = dataSourcesProperties.getFinhub().getUrl();
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * FinHub News API – response is a JSON array, not { "results": [...] }.
     * GET /news?category={category}&token={apiKey}
     */
    public Mono<List<FinHubNewsDto.NewsItem>> fetchNews(String category) {
        String apiKey = dataSourcesProperties.getFinhub().getApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("your-api-key-here")) {
            log.warn("[FINHUB] API key not configured, skipping news fetch");
            return Mono.just(List.of());
        }
        log.info("[FINHUB] Fetching news for category: {}", category);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/news")
                        .queryParam("category", category)
                        .queryParam("token", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<FinHubNewsDto.NewsItem>>() {})
                .doOnError(error -> log.error("[FINHUB] Fetch failed: {}", error.getMessage()))
                .onErrorReturn(List.of());
    }

    /**
     * Company News API
     * GET /company-news?symbol=...&from=...&to=...&token=...
     */
    public Mono<List<FinHubNewsDto.NewsItem>> fetchCompanyNews(String symbol, String from, String to) {
        String apiKey = dataSourcesProperties.getFinhub().getApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("your-api-key-here")) {
            log.warn("[FINHUB] API key not configured, skipping company news fetch");
            return Mono.just(List.of());
        }
        log.info("[FINHUB] Fetching company news for symbol: {}", symbol);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/company-news")
                        .queryParam("symbol", symbol)
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .queryParam("token", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<FinHubNewsDto.NewsItem>>() {})
                .doOnError(error -> log.error("[FINHUB] Company news fetch failed: {}", error.getMessage()))
                .onErrorReturn(List.of());
    }
}