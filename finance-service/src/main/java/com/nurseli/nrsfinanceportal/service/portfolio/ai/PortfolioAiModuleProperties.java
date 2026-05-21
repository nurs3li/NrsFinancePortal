package com.nurseli.nrsfinanceportal.service.portfolio.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "portfolio-ai")
public class PortfolioAiModuleProperties {

    private int dailyLimit = 1;
    private String promptVersion = "v1";

    public int getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }
}
