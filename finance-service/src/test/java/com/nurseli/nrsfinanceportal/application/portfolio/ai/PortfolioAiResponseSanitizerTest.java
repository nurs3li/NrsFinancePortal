package com.nurseli.nrsfinanceportal.application.portfolio.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiAnalysisType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiConcentrationLevel;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiDetailLevel;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiRiskProfile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioAiResponseSanitizerTest {

    private final PortfolioAiResponseSanitizer sanitizer = new PortfolioAiResponseSanitizer(
            new ObjectMapper(),
            new PortfolioAiFallbackGenerator(),
            new PortfolioAiAdviceSanitizer(),
            new PortfolioAiOutputNormalizer(new PortfolioAiAdviceSanitizer())
    );

    @Test
    void addsDisclaimerWhenMissing() {
        String raw = minimalJson("Özet metin.", "Senaryo");
        PortfolioAiParsedOutput out = sanitizer.parseAndSanitize(raw, emptyContext(), false);
        assertThat(out.disclaimer()).isEqualTo(PortfolioAiPromptBuilder.DISCLAIMER);
    }

    @Test
    void satisSenaryosuDoesNotTriggerFallback() {
        String raw = minimalJson(
                "Kısa vadeli satış senaryosunda yoğunlaşma izlenmeli.",
                "Satış senaryosu için dağılım önemli."
        );
        PortfolioAiParsedOutput out = sanitizer.parseAndSanitize(raw, emptyContext(), false);
        assertThat(out.source()).isNull();
        assertThat(out.summary()).containsIgnoringCase("satış senaryosu");
    }

    @Test
    void satilmaliInSummaryIsSanitizedNotFullFallback() {
        String raw = minimalJson("VOO satilmali hemen.", "Senaryo");
        PortfolioAiParsedOutput out = sanitizer.parseAndSanitize(raw, emptyContext(), false);
        assertThat(out.summary().toLowerCase(Locale.ROOT)).doesNotContain("satilmali");
    }

    @Test
    void adviceLanguageSetsFallbackSource() {
        String raw = minimalJson("Hemen al, kesin yükselir.", "");
        PortfolioAiParsedOutput out = sanitizer.parseAndSanitize(raw, emptyContext(), true);
        assertThat(out.source()).isEqualTo(PortfolioAiModels.SOURCE_FALLBACK);
    }

    @Test
    void emptyAssetCommentsFilledFromFallback() {
        String raw = """
                {
                  "portfolioScore": 65,
                  "riskScore": 50,
                  "confidence": "MEDIUM",
                  "concentrationRisk": "MEDIUM",
                  "summary": "Özet",
                  "findings": ["f1"],
                  "scenarioComment": "s",
                  "assetComments": [],
                  "disclaimer": "Bu analiz bilgilendirme amaçlıdır; yatırım tavsiyesi değildir.",
                  "finalNote": "Bu analiz bilgilendirme amaçlıdır; yatırım tavsiyesi değildir."
                }
                """;
        PortfolioAiParsedOutput out = sanitizer.parseAndSanitize(raw, ctxWithVoo(), false);
        assertThat(out.assetComments()).isNotEmpty();
        assertThat(out.assetComments().getFirst().detailComment().toLowerCase())
                .doesNotContain(PortfolioAiAdviceSanitizer.MECHANICAL_PHRASE);
    }

    @Test
    void legacyJsonWithoutNarrativeFieldsParses() throws Exception {
        String legacy = """
                {
                  "portfolioScore": 70,
                  "riskScore": 40,
                  "confidence": "MEDIUM",
                  "concentrationRisk": "HIGH",
                  "summary": "Eski kayıt özeti.",
                  "findings": ["Bulgu"],
                  "scenarioComment": "Senaryo",
                  "assetComments": [],
                  "disclaimer": "Bu analiz bilgilendirme amaçlıdır; yatırım tavsiyesi değildir.",
                  "source": "OPENAI"
                }
                """;
        PortfolioAiParsedOutput out = new ObjectMapper().readValue(legacy, PortfolioAiParsedOutput.class);
        assertThat(out.summary()).isEqualTo("Eski kayıt özeti.");
        assertThat(out.portfolioOverview()).isNull();
    }

    private String minimalJson(String summary, String scenario) {
        return """
                {
                  "portfolioScore": 70,
                  "riskScore": 40,
                  "confidence": "MEDIUM",
                  "concentrationRisk": "HIGH",
                  "summary": "%s",
                  "findings": ["Bulgu 1"],
                  "scenarioComment": "%s",
                  "assetComments": [],
                  "disclaimer": "Bu analiz bilgilendirme amaçlıdır; yatırım tavsiyesi değildir.",
                  "finalNote": "Bu analiz bilgilendirme amaçlıdır; yatırım tavsiyesi değildir."
                }
                """.formatted(summary, scenario);
    }

    private PortfolioAiContextSnapshot ctxWithVoo() {
        return new PortfolioAiContextSnapshot(
                PortfolioAiAnalysisType.GENERAL_REVIEW,
                PortfolioAiRiskProfile.BALANCED,
                PortfolioAiDetailLevel.DETAILED,
                false,
                false,
                true,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                5.0,
                BigDecimal.ZERO,
                3.0,
                false,
                70,
                "VOO",
                40.0,
                PortfolioAiConcentrationLevel.MEDIUM,
                Map.of("FUND", 40.0),
                List.of(),
                List.of(),
                List.of(),
                List.of(new PortfolioAiContextSnapshot.PositionLine(
                        "VOO", "VOO", "FUND", "OPEN", 1, 100.0, 1000, 90.0, 900.0, 100.0, 10.0, 40, 100
                )),
                1,
                0,
                Map.of(),
                List.of(),
                false
        );
    }

    private PortfolioAiContextSnapshot emptyContext() {
        return new PortfolioAiContextSnapshot(
                PortfolioAiAnalysisType.GENERAL_REVIEW,
                PortfolioAiRiskProfile.BALANCED,
                PortfolioAiDetailLevel.DETAILED,
                false,
                false,
                true,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                null,
                false,
                62,
                null,
                0,
                PortfolioAiConcentrationLevel.MEDIUM,
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0,
                0,
                Map.of(),
                List.of(),
                false
        );
    }
}
