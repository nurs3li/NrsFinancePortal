package com.nurseli.nrsfinanceportal.application.viop;

import com.nurseli.nrsfinanceportal.domain.viop.ViopCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ViopContractMultiplierResolverTest {

    @Test
    void equityIsHundred() {
        assertThat(ViopContractMultiplierResolver.resolve("F_AKBNK0726", "AKBNK", ViopCategory.EQUITY))
                .isEqualByComparingTo("100");
        assertThat(ViopContractMultiplierResolver.resolve("F_SISE0726", "SISE", ViopCategory.EQUITY))
                .isEqualByComparingTo("100");
    }

    @Test
    void indexIsTen() {
        assertThat(ViopContractMultiplierResolver.resolve("F_XU0301226", "XU030", ViopCategory.INDEX))
                .isEqualByComparingTo("10");
        assertThat(ViopContractMultiplierResolver.resolve("F_XLBNK1226", "XLBNK", ViopCategory.INDEX))
                .isEqualByComparingTo("10");
    }

    @Test
    void fxIsThousand() {
        assertThat(ViopContractMultiplierResolver.resolve("F_USDTRY0726", "USDTRY", ViopCategory.FX))
                .isEqualByComparingTo("1000");
        assertThat(ViopContractMultiplierResolver.resolve("F_EURTRY0726", "EURTRY", ViopCategory.FX))
                .isEqualByComparingTo("1000");
    }

    @Test
    void ounceGoldIsOne() {
        assertThat(ViopContractMultiplierResolver.resolve("F_XAUUSD1026", "XAUUSD", ViopCategory.COMMODITY))
                .isEqualByComparingTo("1");
    }

    @Test
    void gramGoldXautryAndXautrymAreOne() {
        assertThat(ViopContractMultiplierResolver.resolve("F_XAUTRY1026", "XAUTRY", ViopCategory.COMMODITY))
                .isEqualByComparingTo("1");
        assertThat(ViopContractMultiplierResolver.resolve("F_XAUTRYM1026", "XAUTRYM", ViopCategory.COMMODITY))
                .isEqualByComparingTo("1");
    }

    @Test
    void unknownDefaultsToOne() {
        assertThat(ViopContractMultiplierResolver.resolve("F_UNKNOWN1026", null, null))
                .isEqualByComparingTo("1");
    }
}
