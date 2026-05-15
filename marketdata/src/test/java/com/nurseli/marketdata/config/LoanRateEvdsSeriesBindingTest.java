package com.nurseli.marketdata.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class LoanRateEvdsSeriesBindingTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EvdsProperties.class)
    static class EvdsPropsOnly {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(EvdsPropsOnly.class);

    @Test
    void bindsLoanRateSeriesFromProperties() {
        runner.withPropertyValues(
                        "market.evds.series.LOAN_RATE_CONSUMER_TRY_WEEKLY=CUSTOM_KTF10",
                        "market.evds.series.LOAN_RATE_VEHICLE_TRY_WEEKLY=TP_KTF11"
                )
                .run(ctx -> {
                    EvdsProperties p = ctx.getBean(EvdsProperties.class);
                    assertThat(p.getSeriesCode(EvdsSeriesLogicalNames.LOAN_RATE_CONSUMER_TRY_WEEKLY)).isEqualTo("CUSTOM_KTF10");
                    assertThat(p.getSeriesCode(EvdsSeriesLogicalNames.LOAN_RATE_VEHICLE_TRY_WEEKLY)).isEqualTo("TP_KTF11");
                });
    }
}
