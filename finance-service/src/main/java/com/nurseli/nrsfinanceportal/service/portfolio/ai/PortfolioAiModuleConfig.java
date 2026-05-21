package com.nurseli.nrsfinanceportal.service.portfolio.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({OpenAiProperties.class, PortfolioAiModuleProperties.class})
public class PortfolioAiModuleConfig {
}
