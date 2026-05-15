package com.nurseli.marketdata.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class MarketViopPropertiesPreciousMetalWhitelistTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MarketViopProperties.class)
    static class PropsOnly {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(PropsOnly.class);

    @Test
    void preciousMetalWhitelistBindsFromProperties() {
        runner.withPropertyValues(
                        "app.market.viop.whitelist.precious-metal[0].contract-code=F_XAUTRYM1026",
                        "app.market.viop.whitelist.precious-metal[0].underlying=XAUTRYM",
                        "app.market.viop.whitelist.precious-metal[0].display-name=Gram Altın Vadeli",
                        "app.market.viop.whitelist.precious-metal[0].contract-name=Gram Altın Ekim 2026 Vadeli",
                        "app.market.viop.whitelist.precious-metal[0].maturity-month=10",
                        "app.market.viop.whitelist.precious-metal[0].maturity-year=2026",
                        "app.market.viop.whitelist.precious-metal[0].asset-class=PRECIOUS_METAL",
                        "app.market.viop.whitelist.precious-metal[0].segment=PRECIOUS_METAL_FUTURES",
                        "app.market.viop.whitelist.precious-metal[0].chart-type=PRICE_SERIES",
                        "app.market.viop.whitelist.precious-metal[0].enabled=true",
                        "app.market.viop.whitelist.precious-metal[1].contract-code=F_XAUUSD1026",
                        "app.market.viop.whitelist.precious-metal[1].underlying=XAUUSD",
                        "app.market.viop.whitelist.precious-metal[1].display-name=Ons Altın Vadeli",
                        "app.market.viop.whitelist.precious-metal[1].contract-name=Ons Altın Ekim 2026 Vadeli",
                        "app.market.viop.whitelist.precious-metal[1].maturity-month=10",
                        "app.market.viop.whitelist.precious-metal[1].maturity-year=2026",
                        "app.market.viop.whitelist.precious-metal[1].asset-class=PRECIOUS_METAL",
                        "app.market.viop.whitelist.precious-metal[1].segment=PRECIOUS_METAL_FUTURES",
                        "app.market.viop.whitelist.precious-metal[1].chart-type=PRICE_SERIES",
                        "app.market.viop.whitelist.precious-metal[1].enabled=true")
                .run(ctx -> {
                    MarketViopProperties props = ctx.getBean(MarketViopProperties.class);
                    assertThat(props.getWhitelist().getPreciousMetal()).hasSize(2);
                    assertThat(props.getWhitelist().getPreciousMetal().get(0).getContractCode())
                            .isEqualTo("F_XAUTRYM1026");
                    assertThat(props.getWhitelist().getPreciousMetal().get(1).getAssetClass())
                            .isEqualTo("PRECIOUS_METAL");
                    assertThat(props.getWhitelist().getPreciousMetal().get(1).getChartType())
                            .isEqualTo("PRICE_SERIES");
                });
    }

    @Test
    void equityWhitelistBindsFromProperties() {
        runner.withPropertyValues(
                        "app.market.viop.whitelist.equity[0].contract-code=F_GARAN0726",
                        "app.market.viop.whitelist.equity[0].underlying=GARAN",
                        "app.market.viop.whitelist.equity[0].display-name=GARAN Pay Vadeli",
                        "app.market.viop.whitelist.equity[0].contract-name=GARAN Temmuz 2026 Vadeli",
                        "app.market.viop.whitelist.equity[0].maturity-month=7",
                        "app.market.viop.whitelist.equity[0].maturity-year=2026",
                        "app.market.viop.whitelist.equity[0].asset-class=EQUITY",
                        "app.market.viop.whitelist.equity[0].segment=EQUITY_FUTURES",
                        "app.market.viop.whitelist.equity[0].chart-type=PRICE_SERIES",
                        "app.market.viop.whitelist.equity[0].enabled=true")
                .run(ctx -> {
                    MarketViopProperties props = ctx.getBean(MarketViopProperties.class);
                    assertThat(props.getWhitelist().getEquity()).hasSize(1);
                    assertThat(props.getWhitelist().getEquity().get(0).getContractCode())
                            .isEqualTo("F_GARAN0726");
                    assertThat(props.getWhitelist().getEquity().get(0).getSegment())
                            .isEqualTo("EQUITY_FUTURES");
                });
    }
}
