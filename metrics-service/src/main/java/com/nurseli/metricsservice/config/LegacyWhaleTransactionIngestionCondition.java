package com.nurseli.metricsservice.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public final class LegacyWhaleTransactionIngestionCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var env = context.getEnvironment();
        boolean whaleLegacy = Boolean.parseBoolean(
                env.getProperty("app.whale.legacy-transaction-analysis-enabled", "false"));
        boolean kafkaLegacy = Boolean.parseBoolean(
                env.getProperty("app.kafka.legacy-finance-ingestion-enabled", "false"));
        return whaleLegacy || kafkaLegacy;
    }
}
