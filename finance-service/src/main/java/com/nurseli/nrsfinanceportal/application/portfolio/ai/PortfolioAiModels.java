package com.nurseli.nrsfinanceportal.application.portfolio.ai;

/**
 * finance-service portfolio AI model sabitleri — OpenAI ve fallback kaynak/model tanımlayıcılarını sağlar.
 */
public final class PortfolioAiModels {

    public static final String SOURCE_OPENAI = "OPENAI";
public static final String SOURCE_FALLBACK = "FALLBACK_RULE_BASED";
    public static final String FALLBACK_MODEL = SOURCE_FALLBACK;

    private PortfolioAiModels() {
    }

    /**
     * {@code isOpenAiSource} — Kaynağın OPENAI olup olmadığını kontrol eder.
     */
    public static boolean isOpenAiSource(String source) {
        return SOURCE_OPENAI.equals(source);
    }

    /**
     * {@code isAiGenerated} — Model adının AI üretimi olup olmadığını kontrol eder.
     */
    public static boolean isAiGenerated(String model) {
        return model != null && !model.isBlank() && !FALLBACK_MODEL.equals(model);
    }

    /**
     * {@code sourceFromModel} — Model adından kaynak string'ini türetir.
     */
    public static String sourceFromModel(String model) {
        return isAiGenerated(model) ? SOURCE_OPENAI : SOURCE_FALLBACK;
    }
}
    