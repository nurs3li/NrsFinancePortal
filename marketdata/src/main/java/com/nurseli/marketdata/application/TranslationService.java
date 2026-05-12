package com.nurseli.marketdata.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nurseli.marketdata.config.TranslationProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLDecoder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TranslationService {

    private final TranslationProperties properties;

    private Cache<String, String> translationCache;
    private WebClient plainHttp;
    private WebClient libreHttp;

    @PostConstruct
    void initClientsAndCache() {
        int maxBody = 8 * 1024 * 1024;
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(maxBody))
                .build();
        this.plainHttp = WebClient.builder().exchangeStrategies(strategies).build();
        this.libreHttp = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .exchangeStrategies(strategies)
                .build();
        int maxEntries = Math.max(100, properties.getCacheMaxEntries());
        this.translationCache = Caffeine.newBuilder()
                .maximumSize(maxEntries)
                .expireAfterWrite(Duration.ofHours(72))
                .build();
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public String translate(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (!properties.isEnabled()) {
            return text;
        }
        try {
            return translationCache.get(text, this::translateWithoutCache);
        } catch (Exception ignored) {
            return text;
        }
    }

    private String translateWithoutCache(String text) {
        String provider = properties.getProvider() == null ? "libre" : properties.getProvider().trim().toLowerCase();
        if ("googlefree".equals(provider) || "google".equals(provider)) {
            String fromGoogle = translateWithGoogleFree(text);
            if (fromGoogle != null) {
                return fromGoogle;
            }
            String fromApertium = translateWithApertium(text);
            if (fromApertium != null) {
                return fromApertium;
            }
            String fromMyMemory = translateWithMyMemory(text);
            return fromMyMemory == null ? text : fromMyMemory;
        }
        if ("apertium".equals(provider)) {
            String fromApertium = translateWithApertium(text);
            if (fromApertium != null) {
                return fromApertium;
            }
            String fromGoogle = translateWithGoogleFree(text);
            if (fromGoogle != null) {
                return fromGoogle;
            }
            String fromMyMemory = translateWithMyMemory(text);
            return fromMyMemory == null ? text : fromMyMemory;
        }
        if ("mymemory".equals(provider)) {
            String fromMyMemory = translateWithMyMemory(text);
            return fromMyMemory == null ? text : fromMyMemory;
        }
        String fromLibre = translateWithLibre(text);
        if (fromLibre != null) {
            return fromLibre;
        }
        String fromGoogle = translateWithGoogleFree(text);
        if (fromGoogle != null) {
            return fromGoogle;
        }
        String fromApertium = translateWithApertium(text);
        if (fromApertium != null) {
            return fromApertium;
        }
        String fromMyMemory = translateWithMyMemory(text);
        return fromMyMemory == null ? text : fromMyMemory;
    }

    private String translateWithGoogleFree(String text) {
        try {
            List<String> chunks = splitForPublicTranslator(text, 420);
            if (chunks.isEmpty()) {
                return null;
            }
            StringBuilder out = new StringBuilder();
            for (String chunk : chunks) {
                List<?> response = plainHttp.get()
                        .uri(uriBuilder -> uriBuilder
                                .scheme("https")
                                .host("translate.googleapis.com")
                                .path("/translate_a/single")
                                .queryParam("client", "gtx")
                                .queryParam("sl", "auto")
                                .queryParam("tl", "tr")
                                .queryParam("dt", "t")
                                .queryParam("q", chunk)
                                .build()
                        )
                        .retrieve()
                        .bodyToMono(List.class)
                        .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                        .onErrorReturn(List.of())
                        .block();
                if (response == null || response.isEmpty() || !(response.get(0) instanceof List<?> translatedParts)) {
                    return null;
                }
                StringBuilder chunkOut = new StringBuilder();
                for (Object part : translatedParts) {
                    if (part instanceof List<?> row && !row.isEmpty() && row.get(0) instanceof String piece) {
                        chunkOut.append(piece);
                    }
                }
                if (chunkOut.isEmpty()) {
                    return null;
                }
                if (!out.isEmpty()) {
                    out.append(' ');
                }
                out.append(chunkOut);
            }
            return out.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> splitForPublicTranslator(String text, int maxChars) {
        String input = text == null ? "" : text.trim();
        if (input.isEmpty()) {
            return List.of();
        }
        if (input.length() <= maxChars) {
            return List.of(input);
        }
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String token : input.split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            int nextLen = current.isEmpty() ? token.length() : current.length() + 1 + token.length();
            if (nextLen > maxChars && !current.isEmpty()) {
                out.add(current.toString());
                current.setLength(0);
            }
            if (token.length() > maxChars) {
                if (!current.isEmpty()) {
                    out.add(current.toString());
                    current.setLength(0);
                }
                out.add(token);
                continue;
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(token);
        }
        if (!current.isEmpty()) {
            out.add(current.toString());
        }
        return out;
    }

    private String translateWithLibre(String text) {
        try {
            Map<String, Object> body = Map.of(
                    "q", text,
                    "source", "auto",
                    "target", "tr",
                    "format", "text"
            );
            Map<?, ?> response = libreHttp.post()
                    .uri("/translate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .onErrorReturn(Map.of())
                    .block();
            if (response == null || response.isEmpty()) {
                return null;
            }
            Object translated = response.get("translatedText");
            if (translated instanceof String t && !t.isBlank()) {
                return t;
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String translateWithMyMemory(String text) {
        try {
            Map<?, ?> response = plainHttp.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.mymemory.translated.net")
                            .path("/get")
                            .queryParam("q", text)
                            .queryParam("langpair", "en|tr")
                            .build()
                    )
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .onErrorReturn(Map.of())
                    .block();
            if (response == null || response.isEmpty()) {
                return null;
            }
            Object details = response.get("responseDetails");
            if (details instanceof String d && d.toUpperCase().contains("QUERY LENGTH LIMIT EXCEEDED")) {
                return null;
            }
            Object responseData = response.get("responseData");
            if (responseData instanceof Map<?, ?> data) {
                Object translated = data.get("translatedText");
                if (translated instanceof String t && !t.isBlank()) {
                    if (t.contains("%20") || t.contains("%2F") || t.contains("%3A")) {
                        try {
                            String decoded = URLDecoder.decode(t, java.nio.charset.StandardCharsets.UTF_8);
                            if (!decoded.isBlank()) {
                                return decoded;
                            }
                        } catch (Exception ignored) {
                            // keep original translated text if decode fails
                        }
                    }
                    return t;
                }
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String translateWithApertium(String text) {
        try {
            Map<?, ?> response = plainHttp.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("www.apertium.org")
                            .path("/apy/translate")
                            .queryParam("langpair", "en|tr")
                            .queryParam("q", text)
                            .build()
                    )
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .onErrorReturn(Map.of())
                    .block();
            if (response == null || response.isEmpty()) {
                return null;
            }
            Object status = response.get("responseStatus");
            if (status instanceof Number n && n.intValue() != 200) {
                return null;
            }
            Object responseData = response.get("responseData");
            if (responseData instanceof Map<?, ?> data) {
                Object translated = data.get("translatedText");
                if (translated instanceof String t && !t.isBlank()) {
                    return t;
                }
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
