package com.nurseli.nrsfinanceportal.application.portfolio.ai;

import com.nurseli.nrsfinanceportal.config.OpenAiProperties;
import com.nurseli.nrsfinanceportal.config.PortfolioAiModuleProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * finance-service portfolio AI modül konfigürasyonu — OpenAI ve modül properties bean'lerini etkinleştirir.
 */
@Configuration
@EnableConfigurationProperties({OpenAiProperties.class, PortfolioAiModuleProperties.class})

public class PortfolioAiModuleConfig {
}
