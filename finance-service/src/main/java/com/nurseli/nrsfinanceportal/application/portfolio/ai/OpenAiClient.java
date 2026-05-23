package com.nurseli.nrsfinanceportal.application.portfolio.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * finance-service OpenAI istemcisi — portfolio AI analizi için OpenAI Chat Completions API'sini JSON schema ile çağırır.
 */
@Slf4j
@Component

public class OpenAiClient {

    private static final Logger log = LogManager.getLogger(OpenAiClient.class);

    private final WebClient openAiWebClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * {@code OpenAiClient} — WebClient, ObjectMapper ve OpenAiProperties bağımlılıklarını enjekte eden public constructor.
     */
    public OpenAiClient(
            @Qualifier("openAiWebClient") WebClient openAiWebClient,
            OpenAiProperties properties,
    ObjectMapper objectMapper
    ) {
        this.openAiWebClient = openAiWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * {@code analyzePortfolioJson} — Sistem/geliştirici/kullanıcı prompt'larıyla OpenAI'dan yapılandırılmış portfolio analiz JSON yanıtı ister.
     */
    public OpenAiCallResult analyzePortfolioJson(
            String systemPrompt,
            String developerPrompt,
    String userPrompt,
            String promptVersion,
            int assetTargetCount,
            int contextSizeChars
    ) {
        if (!properties.isConfigured()) {
            log.info(
                    "event=portfolio_ai_openai_call success=false failureReason={} durationMs=0 model={} promptVersion={} assetTargetCount={} contextSizeChars={}",
                    OpenAiFailureReason.NOT_CONFIGURED,
                    properties.getModel(),
                    promptVersion,
                    assetTargetCount,
                    contextSizeChars
            );
            return new OpenAiCallResult(Optional.empty(), OpenAiFailureReason.NOT_CONFIGURED, 0);
        }
        long started = System.currentTimeMillis();
        try {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("model", properties.getModel());
            body.put("temperature", properties.getTemperature());
            body.put("response_format", PortfolioAiResponseJsonSchema.responseFormat());
            body.put(
                    "messages",
                    List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", developerPrompt + "\n\n" + userPrompt)
                    )
            );
            String raw = openAiWebClient.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .block(Duration.ofSeconds(properties.getTimeoutSeconds() + 5L));
            long durationMs = System.currentTimeMillis() - started;
            if (raw == null || raw.isBlank()) {
                log.warn(
                        "event=portfolio_ai_openai_call success=false failureReason={} durationMs={} model={} promptVersion={} assetTargetCount={} contextSizeChars={}",
                        OpenAiFailureReason.UNKNOWN,
                        durationMs,
                        properties.getModel(),
                        promptVersion,
                        assetTargetCount,
                        contextSizeChars
                );
                return new OpenAiCallResult(Optional.empty(), OpenAiFailureReason.UNKNOWN, durationMs);
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                log.warn(
                        "event=portfolio_ai_openai_call success=false failureReason={} durationMs={} model={} promptVersion={} assetTargetCount={} contextSizeChars={}",
                        OpenAiFailureReason.UNKNOWN,
                        durationMs,
                        properties.getModel(),
                        promptVersion,
                        assetTargetCount,
                        contextSizeChars
                );
                return new OpenAiCallResult(Optional.empty(), OpenAiFailureReason.UNKNOWN, durationMs);
            }
            log.info(
                    "event=portfolio_ai_openai_call success=true failureReason={} durationMs={} model={} promptVersion={} assetTargetCount={} contextSizeChars={}",
                    OpenAiFailureReason.NONE,
                    durationMs,
                    properties.getModel(),
                    promptVersion,
                    assetTargetCount,
                    contextSizeChars
            );
            return new OpenAiCallResult(Optional.of(content.asText()), OpenAiFailureReason.NONE, durationMs);
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - started;
            OpenAiFailureReason reason = OpenAiExceptionClassifier.classify(ex);
            log.warn(
                    "event=portfolio_ai_openai_call success=false failureReason={} durationMs={} model={} promptVersion={} assetTargetCount={} contextSizeChars={} errorType={}",
                    reason,
                    durationMs,
                    properties.getModel(),
                    promptVersion,
                    assetTargetCount,
                    contextSizeChars,
                    ex.getClass().getSimpleName()
            );
            return new OpenAiCallResult(Optional.empty(), reason, durationMs);
        }
    }
}
