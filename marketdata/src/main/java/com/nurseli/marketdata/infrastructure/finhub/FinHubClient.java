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
    /**
     * FinHub Quote API – hisse senedi anlık fiyat.
     * GET /quote?symbol=...&token=...
     */
    public Mono<FinHubQuoteDto> fetchQuote(String symbol) {
        String apiKey = dataSourcesProperties.getFinhub().getApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("your-api-key-here")) {
            log.warn("[FINHUB] API key not configured, skipping quote fetch");
            return Mono.empty();
        }
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/quote")
                        .queryParam("symbol", symbol)
                        .queryParam("token", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(FinHubQuoteDto.class)
                .doOnError(error -> log.error("[FINHUB] Quote fetch failed for {}: {}", symbol, error.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * Finnhub stock candles.
     * GET /stock/candle?symbol=...&resolution=D&from=...&to=...&token=...
     */
    public Mono<FinHubCandleDto> fetchDailyCandles(String symbol, long fromEpochSec, long toEpochSec) {
        String apiKey = dataSourcesProperties.getFinhub().getApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("your-api-key-here")) {
            log.warn("[FINHUB] API key not configured, skipping candle fetch");
            return Mono.empty();
        }
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stock/candle")
                        .queryParam("symbol", symbol)
                        .queryParam("resolution", "D")
                        .queryParam("from", fromEpochSec)
                        .queryParam("to", toEpochSec)
                        .queryParam("token", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(FinHubCandleDto.class)
                .doOnError(error -> log.error("[FINHUB] Candle fetch failed for {}: {}", symbol, error.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    /**
     * FinHub Company Profile 2 API.
     * GET /stock/profile2?symbol=...&token=...
     */
    public Mono<FinHubCompanyProfileDto> fetchCompanyProfile(String symbol) {
        String apiKey = dataSourcesProperties.getFinhub().getApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("your-api-key-here")) {
            log.warn("[FINHUB] API key not configured, skipping profile fetch");
            return Mono.empty();
        }
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/stock/profile2")
                        .queryParam("symbol", symbol)
                        .queryParam("token", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(FinHubCompanyProfileDto.class)
                .doOnError(error -> log.error("[FINHUB] Profile fetch failed for {}: {}", symbol, error.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }
}