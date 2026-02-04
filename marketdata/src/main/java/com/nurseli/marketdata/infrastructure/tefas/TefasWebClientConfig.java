package com.nurseli.marketdata.infrastructure.tefas;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders; // Bunu ekle
import org.springframework.http.MediaType;   // Bunu ekle
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class TefasWebClientConfig {
    @Bean
    public WebClient tefasWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl("https://www.tefas.gov.tr")
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                .defaultHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
                // 🔥 KRİTİK: TEFAS kendi içinden çağrılmayan istekleri reddeder
                .defaultHeader("Referer", "https://www.tefas.gov.tr/FonAnaliz.aspx")
                .defaultHeader("X-Requested-With", "XMLHttpRequest")
                .build();
    }
}