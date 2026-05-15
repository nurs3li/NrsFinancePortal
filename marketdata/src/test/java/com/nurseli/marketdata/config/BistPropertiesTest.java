package com.nurseli.marketdata.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class BistPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BistProperties.class)
    static class PropsOnly {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(PropsOnly.class);

    @Test
    void defaultsMatchSpecification() {
        runner.run(ctx -> {
            BistProperties p = ctx.getBean(BistProperties.class);
            assertThat(p.isEnabled()).isFalse();
            assertThat(p.isSchedulerEnabled()).isFalse();
            assertThat(p.isSeedOnStart()).isFalse();
            assertThat(p.getSeedLookbackDays()).isEqualTo(730);
            assertThat(p.getSeedMaxSymbols()).isEqualTo(20);
            assertThat(p.getSchedulerIncrementalLookbackDays()).isEqualTo(30);
            assertThat(p.getSchedulerZone()).isEqualTo("Europe/Istanbul");
            assertThat(p.getDailyIngestCron()).isEqualTo("0 20 7 * * MON-FRI");
            assertThat(p.getPrimaryProvider()).isEqualTo("IS_YATIRIM");
            assertThat(p.getFallbackProvider()).isEqualTo("YAHOO");
            assertThat(p.isFallbackToDb()).isTrue();
            assertThat(p.getDefaultLookbackYears()).isEqualTo(2);
            assertThat(p.getConnectTimeoutMs()).isEqualTo(5000);
            assertThat(p.getReadTimeoutMs()).isEqualTo(45000);
            assertThat(p.getHistoryFetchChunkDays()).isEqualTo(90);
            assertThat(p.getDelayMsBetweenHistoryChunks()).isEqualTo(150);
            assertThat(p.getSymbols()).isEmpty();
        });
    }

    @Test
    void bindsFromPropertyValues() {
        runner.withPropertyValues(
                        "market.bist.enabled=true",
                        "market.bist.scheduler-enabled=true",
                        "market.bist.seed-on-start=true",
                        "market.bist.seed-lookback-days=60",
                        "market.bist.seed-max-symbols=3",
                        "market.bist.scheduler-incremental-lookback-days=14",
                        "market.bist.scheduler-zone=UTC",
                        "market.bist.daily-ingest-cron=0 0 12 * * *",
                        "market.bist.primary-provider=YAHOO",
                        "market.bist.fallback-provider=IS_YATIRIM",
                        "market.bist.fallback-to-db=false",
                        "market.bist.default-lookback-years=5",
                        "market.bist.symbols=THYAO,ASELS"
                )
                .run(ctx -> {
                    BistProperties p = ctx.getBean(BistProperties.class);
                    assertThat(p.isEnabled()).isTrue();
                    assertThat(p.isSchedulerEnabled()).isTrue();
                    assertThat(p.isSeedOnStart()).isTrue();
                    assertThat(p.getSeedLookbackDays()).isEqualTo(60);
                    assertThat(p.getSeedMaxSymbols()).isEqualTo(3);
                    assertThat(p.getSchedulerIncrementalLookbackDays()).isEqualTo(14);
                    assertThat(p.getSchedulerZone()).isEqualTo("UTC");
                    assertThat(p.getDailyIngestCron()).isEqualTo("0 0 12 * * *");
                    assertThat(p.getPrimaryProvider()).isEqualTo("YAHOO");
                    assertThat(p.getFallbackProvider()).isEqualTo("IS_YATIRIM");
                    assertThat(p.isFallbackToDb()).isFalse();
                    assertThat(p.getDefaultLookbackYears()).isEqualTo(5);
                    assertThat(p.getSymbols()).containsExactly("THYAO", "ASELS");
                });
    }
}
