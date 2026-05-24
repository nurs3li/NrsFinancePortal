package com.nurseli.marketdata.infrastructure.bist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BistSymbolCatalogTest {

    private BistSymbolCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new BistSymbolCatalog();
    }

    @Test
    void getAll_returnsCatalogSymbols() {
        assertThat(catalog.getAll()).hasSize(23);
    }

    @Test
    void thyao_supported_caseInsensitive() {
        assertThat(catalog.isSupported("thyao")).isTrue();
        assertThat(catalog.isSupported("ThYaO")).isTrue();
        assertThat(catalog.isSupported(" THYAO ")).isTrue();
    }

    @Test
    void asels_garan_tuprs_supported() {
        assertThat(catalog.isSupported("ASELS")).isTrue();
        assertThat(catalog.isSupported("GARAN")).isTrue();
        assertThat(catalog.isSupported("TUPRS")).isTrue();
    }

    @Test
    void vesbe_karsn_zoren_akbnk_tcell_supported() {
        assertThat(catalog.isSupported("VESBE")).isTrue();
        assertThat(catalog.isSupported("KARSN")).isTrue();
        assertThat(catalog.isSupported("ZOREN")).isTrue();
        assertThat(catalog.isSupported("AKBNK")).isTrue();
        assertThat(catalog.isSupported("TCELL")).isTrue();
        assertThat(catalog.toYahooSymbol("VESBE")).isEqualTo("VESBE.IS");
    }

    @Test
    void unknownSymbol_notSupported() {
        assertThat(catalog.isSupported("UNKNOWN")).isFalse();
        assertThat(catalog.isSupported("AAPL")).isFalse();
    }

    @Test
    void blankOrNull_notSupported() {
        assertThat(catalog.isSupported("")).isFalse();
        assertThat(catalog.isSupported("   ")).isFalse();
        assertThat(catalog.isSupported(null)).isFalse();
    }

    @Test
    void thyao_yahooAndIsYatirimCodes() {
        assertThat(catalog.toIsYatirimSymbol("thyao")).isEqualTo("THYAO");
        assertThat(catalog.toYahooSymbol("thyao")).isEqualTo("THYAO.IS");
    }

    @Test
    void yahooSuffix_normalizedForLookup() {
        assertThat(catalog.isSupported("thyao.is")).isTrue();
        assertThat(catalog.findBySymbol("THYAO.IS")).isPresent();
        assertThat(catalog.toYahooSymbol("THYAO.IS")).isEqualTo("THYAO.IS");
    }

    @Test
    void findBySymbol_returnsMetadata() {
        Optional<BistSymbolMetadata> row = catalog.findBySymbol("GARAN");
        assertThat(row).isPresent();
        assertThat(row.get().exchange()).isEqualTo("BIST");
        assertThat(row.get().currency()).isEqualTo("TRY");
        assertThat(row.get().assetType()).isEqualTo("EQUITY");
        assertThat(row.get().country()).isEqualTo("TR");
        assertThat(row.get().yahooSymbol()).isEqualTo("GARAN.IS");
        assertThat(row.get().isYatirimSymbol()).isEqualTo("GARAN");
    }

    @Test
    void toIsYatirimSymbol_unknown_throws() {
        assertThatThrownBy(() -> catalog.toIsYatirimSymbol("ZZZZ"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported BIST symbol");
    }

    @Test
    void toYahooSymbol_unknown_throws() {
        assertThatThrownBy(() -> catalog.toYahooSymbol("NOPE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported BIST symbol");
    }
}
