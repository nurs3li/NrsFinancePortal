package com.nurseli.marketdata.application;

import com.nurseli.marketdata.application.viop.ViopContractParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViopContractParserTest {

  private final ViopContractParser parser = new ViopContractParser();

  @ParameterizedTest
  @CsvSource({
    "F_XAUUSD0626, XAUUSD0626",
    "xauusd0626, XAUUSD0626",
    "'', ''",
    "'   ', '   '"
  })
  void normalizeContractCode_trimsPrefixAndUppercases(String input, String expected) {
    assertEquals(expected, parser.normalizeContractCode(input));
  }

  @Test
  void inferExpiryFromContractCode_usesFallbackWhenProvided() {
    assertEquals("2026-06-30", parser.inferExpiryFromContractCode("XAUUSD0626", "2026-06-30"));
  }

  @Test
  void inferExpiryFromContractCode_parsesMmyySuffix() {
    assertEquals("2026-06-30", parser.inferExpiryFromContractCode("XAUUSD0626", null));
    assertEquals("2025-12-31", parser.inferExpiryFromContractCode("F_USDTRY1225", null));
  }

  @Test
  void inferExpiryFromContractCode_returnsNullForInvalidMonth() {
    assertNull(parser.inferExpiryFromContractCode("XAUUSD1326", null));
  }

  @Test
  void inferExpiryFromContractCode_returnsNullWhenPatternMissing() {
    assertNull(parser.inferExpiryFromContractCode("XAUUSD", null));
  }

  @Test
  void calculateDaysToExpiry_returnsPositiveDaysForFutureDate() {
    Integer days = parser.calculateDaysToExpiry("2099-12-31");

    assertNotNull(days);
    assertTrue(days > 0);
  }

  @Test
  void calculateDaysToExpiry_returnsNullForBlankOrInvalid() {
    assertNull(parser.calculateDaysToExpiry(null));
    assertNull(parser.calculateDaysToExpiry("not-a-date"));
  }
}
