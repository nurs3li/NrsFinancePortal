package com.nurseli.marketdata.domain.metal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PreciousMetalUsdCatalogTest {

    @Test
    void mapsCanonicalToProvider() {
        assertThat(PreciousMetalUsdCatalog.byCanonicalOrNull("XAU_USD_OZ").providerSymbol()).isEqualTo("XAUUSD");
        assertThat(PreciousMetalUsdCatalog.byCanonicalOrNull("XAG_USD_OZ").providerSymbol()).isEqualTo("XAGUSD");
        assertThat(PreciousMetalUsdCatalog.byCanonicalOrNull("XPT_USD_OZ").providerSymbol()).isEqualTo("XPTUSD");
        assertThat(PreciousMetalUsdCatalog.byCanonicalOrNull("XPD_USD_OZ").providerSymbol()).isEqualTo("XPDUSD");
    }

    @Test
    void allContainsFourEntries() {
        assertThat(PreciousMetalUsdCatalog.all()).hasSize(4);
    }
}
