package com.nurseli.nrsfinanceportal.application.portfolio.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * finance-service portfolio AI modül özellikleri — günlük analiz limiti ve prompt versiyonu ayarlarını tutar.
 */
@ConfigurationProperties(prefix = "portfolio-ai")

public class PortfolioAiModuleProperties {

    private int dailyLimit = 1;
    private String promptVersion = "v1";

    /**
     * {@code getDailyLimit} — Kullanıcı başına günlük AI analiz limitini döner.
     */
    public int getDailyLimit() {
        return dailyLimit;
    }

    /**
     * {@code setDailyLimit} — Günlük AI analiz limitini ayarlar.
     */
    public void setDailyLimit(int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    /**
     * {@code getPromptVersion} — Aktif prompt versiyonunu döner.
     */
    public String getPromptVersion() {
        return promptVersion;
    }

    /**
     * {@code setPromptVersion} — Prompt versiyonunu ayarlar.
     */
    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }
}
    