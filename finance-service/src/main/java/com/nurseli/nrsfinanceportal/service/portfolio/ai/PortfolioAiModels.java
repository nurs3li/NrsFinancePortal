package com.nurseli.nrsfinanceportal.service.portfolio.ai;

public final class PortfolioAiModels {

    public static final String SOURCE_OPENAI = "OPENAI";
    public static final String SOURCE_FALLBACK = "FALLBACK_RULE_BASED";
    /** DB {@code model} kolonu — fallback kayıtlarında kullanılır. */
    public static final String FALLBACK_MODEL = SOURCE_FALLBACK;

    private PortfolioAiModels() {
    }

    public static boolean isOpenAiSource(String source) {
        return SOURCE_OPENAI.equals(source);
    }

    public static boolean isAiGenerated(String model) {
        return model != null && !model.isBlank() && !FALLBACK_MODEL.equals(model);
    }

    public static String sourceFromModel(String model) {
        return isAiGenerated(model) ? SOURCE_OPENAI : SOURCE_FALLBACK;
    }
}
