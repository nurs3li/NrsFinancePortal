package com.nurseli.nrsfinanceportal.service.portfolio.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.nrsfinanceportal.common.dto.portfolioai.PortfolioAiAnalysisRequest;
import com.nurseli.nrsfinanceportal.common.dto.portfolioai.PortfolioAiAnalysisResponse;
import com.nurseli.nrsfinanceportal.common.dto.portfolioai.PortfolioAiHistoryItemDto;
import com.nurseli.nrsfinanceportal.common.dto.portfolioai.PortfolioAiHistoryListResponse;
import com.nurseli.nrsfinanceportal.common.dto.portfolioai.PortfolioAiUsageResponse;
import com.nurseli.nrsfinanceportal.common.exception.ApiBusinessException;
import com.nurseli.nrsfinanceportal.common.response.ApiErrorCode;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiAnalysisEntity;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.PortfolioAiAnalysisRepository;
import com.nurseli.nrsfinanceportal.service.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PortfolioAiAnalysisService {

    private static final Logger log = LogManager.getLogger(PortfolioAiAnalysisService.class);
    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");

    private final CurrentUserResolver currentUserResolver;
    private final PortfolioAiUsageService usageService;
    private final PortfolioAiContextBuilder contextBuilder;
    private final PortfolioAiPromptBuilder promptBuilder;
    private final OpenAiClient openAiClient;
    private final OpenAiProperties openAiProperties;
    private final PortfolioAiResponseSanitizer sanitizer;
    private final PortfolioAiFallbackGenerator fallbackGenerator;
    private final PortfolioAiAnalysisRepository repository;
    private final PortfolioAiMapper mapper;
    private final PortfolioAiModuleProperties moduleProperties;
    private final ObjectMapper objectMapper;
    private final PortfolioAiReportEmailSender reportEmailSender;

    @Transactional(readOnly = true)
    public PortfolioAiUsageResponse usage() {
        return usageService.usageForCurrentUser();
    }

    @Transactional(readOnly = true)
    public PortfolioAiAnalysisResponse latest() {
        Long userId = currentUserResolver.getCurrentUserId();
        PortfolioAiAnalysisEntity entity = repository.findFirstByUser_IdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> notFound());
        return mapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public PortfolioAiHistoryListResponse history(String range, String query) {
        Long userId = currentUserResolver.getCurrentUserId();
        Instant from = historyFrom(range);
        List<PortfolioAiHistoryItemDto> items = repository
                .findByUser_IdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(userId, from)
                .stream()
                .map(mapper::toHistoryItem)
                .filter(item -> matchesQuery(item, query))
                .toList();
        return new PortfolioAiHistoryListResponse(items);
    }

    @Transactional(readOnly = true)
    public PortfolioAiAnalysisResponse getById(String id) {
        Long userId = currentUserResolver.getCurrentUserId();
        PortfolioAiAnalysisEntity entity = repository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> notFound());
        return mapper.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public void sendAnalysisReportEmail(String id) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        String email = user.getEmail() != null ? user.getEmail().trim() : "";
        if (email.isEmpty()) {
            throw new ApiBusinessException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.BAD_REQUEST,
                    "Hesabınızda kayıtlı e-posta bulunamadı."
            );
        }
        PortfolioAiAnalysisResponse response = getById(id);
        reportEmailSender.sendReport(email, response);
    }

    @Transactional
    public void deleteAnalysis(String id) {
        Long userId = currentUserResolver.getCurrentUserId();
        PortfolioAiAnalysisEntity entity = repository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> notFound());
        repository.delete(entity);
    }

    @Transactional
    public PortfolioAiAnalysisResponse analyzeCurrent(PortfolioAiAnalysisRequest request) {
        usageService.assertDailyQuotaAvailable();
        User user = currentUserResolver.getOrCreateCurrentUser();
        Long userId = user.getId();

        PortfolioAiContextSnapshot snapshot = contextBuilder.buildSnapshot(request);
        if (snapshot.openCount() <= 0) {
            throw new ApiBusinessException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.BAD_REQUEST,
                    "AI analizi için en az bir açık pozisyon gerekir."
            );
        }

        String contextJson = contextBuilder.toCompactContextJson(snapshot);
        int assetTargetCount = snapshot.assetCommentTargets().size();
        int contextSizeChars = contextJson.length();
        String requestHash = PortfolioAiRequestHashUtil.hash(userId, request, snapshot);
        String promptVersion = moduleProperties.getPromptVersion();

        log.info(
                "portfolio_ai_analysis_start userId={} analysisType={} requestHash={} model={} promptVersion={}",
                maskUserId(userId),
                request.analysisType(),
                requestHash,
                openAiProperties.getModel(),
                promptVersion
        );

        long started = System.currentTimeMillis();
        PortfolioAiParsedOutput output;
        String modelUsed;
        boolean openAiSuccess = false;

        OpenAiCallResult openAiResult = openAiClient.analyzePortfolioJson(
                promptBuilder.systemPrompt(),
                promptBuilder.developerPrompt(),
                promptBuilder.userPrompt(contextJson),
                promptVersion,
                assetTargetCount,
                contextSizeChars
        );

        if (openAiResult.content().isPresent()) {
            String raw = openAiResult.content().get();
            boolean advice = sanitizer.containsAdviceText(raw);
            output = sanitizer.parseAndSanitize(raw, snapshot, advice);
            if (PortfolioAiModels.SOURCE_FALLBACK.equals(output.source())) {
                modelUsed = PortfolioAiModels.FALLBACK_MODEL;
            } else {
                output = output.withSource(PortfolioAiModels.SOURCE_OPENAI);
                modelUsed = openAiProperties.getModel();
                openAiSuccess = true;
            }
        } else {
            output = fallbackGenerator.build(snapshot).withSource(PortfolioAiModels.SOURCE_FALLBACK);
            modelUsed = PortfolioAiModels.FALLBACK_MODEL;
        }

        long durationMs = System.currentTimeMillis() - started;
        log.info(
                "portfolio_ai_analysis_finish userId={} analysisType={} requestHash={} model={} promptVersion={} durationMs={} success={}",
                maskUserId(userId),
                request.analysisType(),
                requestHash,
                modelUsed,
                promptVersion,
                durationMs,
                openAiSuccess
        );

        String id = UUID.randomUUID().toString();
        String snapshotStored = contextJson;
        String outputStored = writeOutputJson(output);

        String title = request.title().trim();
        PortfolioAiAnalysisEntity entity = new PortfolioAiAnalysisEntity(
                id,
                user,
                title,
                request.analysisType(),
                request.riskProfile(),
                request.detailLevel(),
                snapshotStored,
                outputStored,
                output.portfolioScore(),
                output.riskScore(),
                output.confidence(),
                output.concentrationRisk(),
                requestHash,
                modelUsed,
                promptVersion,
                Instant.now()
        );
        repository.save(entity);
        return mapper.toResponse(entity, output);
    }

    private String writeOutputJson(PortfolioAiParsedOutput output) {
        try {
            return objectMapper.writeValueAsString(output);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("AI output serialization failed", ex);
        }
    }

    private Instant historyFrom(String range) {
        if (range == null || range.isBlank() || "all".equalsIgnoreCase(range)) {
            return Instant.EPOCH;
        }
        String r = range.trim().toLowerCase();
        LocalDate today = LocalDate.now(TZ);
        if (r.equals("7d")) {
            return today.minusDays(7).atStartOfDay(TZ).toInstant();
        }
        return today.minusDays(30).atStartOfDay(TZ).toInstant();
    }

    private boolean matchesQuery(PortfolioAiHistoryItemDto item, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String q = query.trim().toLowerCase();
        return item.title().toLowerCase().contains(q)
                || item.summary().toLowerCase().contains(q);
    }

    private ApiBusinessException notFound() {
        return new ApiBusinessException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.AI_ANALYSIS_NOT_FOUND,
                "AI analizi bulunamadı."
        );
    }

    private String maskUserId(Long userId) {
        if (userId == null) {
            return "null";
        }
        return "u" + (userId % 10000);
    }
}
