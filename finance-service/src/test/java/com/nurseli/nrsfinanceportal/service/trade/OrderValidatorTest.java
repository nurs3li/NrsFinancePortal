package com.nurseli.nrsfinanceportal.service.trade;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.trade.TradeType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;

class OrderValidatorTest {

    @Test
    void infersTemplateWhenMissing() {
        OrderValidator validator = buildValidator(false);
        TradeRequest req = new TradeRequest(AssetType.CRYPTO, "BTCUSDT", BigDecimal.ONE, TradeType.BUY, null, null);
        TradeRequest normalized = validator.normalizeAndValidate(req);
        Assertions.assertEquals(OrderTemplateType.SPOT, normalized.templateType());
    }

    @Test
    void infersFuturesFromSymbolPatternWhenMissing() {
        OrderValidator validator = buildValidator(false);
        TradeRequest req = new TradeRequest(AssetType.FX, "USDTRY0626", BigDecimal.ONE, TradeType.BUY, null, null);
        TradeRequest normalized = validator.normalizeAndValidate(req);
        Assertions.assertEquals(OrderTemplateType.FUTURES, normalized.templateType());
    }

    @Test
    void rejectsInvalidTemplateAssetPairWhenV2Enabled() {
        OrderValidator validator = buildValidator(true);
        TradeRequest req = new TradeRequest(AssetType.CRYPTO, "BTCUSDT", BigDecimal.ONE, TradeType.BUY, OrderTemplateType.FIXED_INCOME, Map.of());
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () -> validator.normalizeAndValidate(req));
        Assertions.assertTrue(ex.getMessage().contains("MARKET_ORDER_RESTRICTION"));
    }

    @Test
    void rejectsTemplateWhenSymbolImpliesDifferentInstrument() {
        OrderValidator validator = buildValidator(true);
        TradeRequest req = new TradeRequest(AssetType.FUND, "TRT120228T10", BigDecimal.ONE, TradeType.BUY, OrderTemplateType.SPOT, Map.of());
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () -> validator.normalizeAndValidate(req));
        Assertions.assertTrue(ex.getMessage().contains("MARKET_ORDER_RESTRICTION"));
    }

    private OrderValidator buildValidator(boolean enabled) {
        OrderValidator validator = new OrderValidator(new OrderTemplateConstraintService());
        ReflectionTestUtils.setField(validator, "orderFormV2Enabled", enabled);
        return validator;
    }
}
