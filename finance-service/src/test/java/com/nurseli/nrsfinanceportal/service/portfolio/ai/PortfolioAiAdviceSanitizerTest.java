package com.nurseli.nrsfinanceportal.service.portfolio.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioAiAdviceSanitizerTest {

    private final PortfolioAiAdviceSanitizer sanitizer = new PortfolioAiAdviceSanitizer();

    @Test
    void satisSenaryosuIsAllowed() {
        assertThat(sanitizer.containsStillBanned("Kısa vadeli satış senaryosunda dikkat edilmeli.")).isFalse();
    }

    @Test
    void satilmaliIsSanitized() {
        String out = sanitizer.sanitizeText("BTC satilmali.");
        assertThat(out).doesNotContainIgnoringCase("satilmali");
        assertThat(sanitizer.containsStillBanned(out)).isFalse();
    }

    @Test
    void kesinYukselirIsSanitized() {
        String out = sanitizer.sanitizeText("Kesin yükselir.");
        assertThat(out.toLowerCase()).contains("potansiyel");
        assertThat(sanitizer.containsStillBanned(out)).isFalse();
    }

    @Test
    void hemenAlStillBanned() {
        assertThat(sanitizer.containsStillBanned("Hemen al bugün.")).isTrue();
    }
}
