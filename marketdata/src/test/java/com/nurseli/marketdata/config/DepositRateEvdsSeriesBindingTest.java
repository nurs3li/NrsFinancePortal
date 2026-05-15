package com.nurseli.marketdata.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class DepositRateEvdsSeriesBindingTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EvdsProperties.class)
    static class EvdsPropsOnly {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(EvdsPropsOnly.class);

    @Test
    void bindsDepositSeriesFromEvdsEnvStyleProperties() {
        runner.withPropertyValues(
                        "market.evds.series.DEPOSIT_RATE_TRY_1M_WEEKLY=CUSTOM_MT01",
                        "market.evds.series.DEPOSIT_RATE_USD_1Y_WEEKLY=TP_USD_MT04"
                )
                .run(ctx -> {
                    EvdsProperties p = ctx.getBean(EvdsProperties.class);
                    assertThat(p.getSeriesCode(EvdsSeriesLogicalNames.DEPOSIT_RATE_TRY_1M_WEEKLY)).isEqualTo("CUSTOM_MT01");
                    assertThat(p.getSeriesCode(EvdsSeriesLogicalNames.DEPOSIT_RATE_USD_1Y_WEEKLY)).isEqualTo("TP_USD_MT04");
                });
    }
}
