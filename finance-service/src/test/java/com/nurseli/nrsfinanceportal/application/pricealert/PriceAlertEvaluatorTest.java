package com.nurseli.nrsfinanceportal.application.pricealert;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.pricealert.PriceAlert;
import com.nurseli.nrsfinanceportal.domain.pricealert.PriceAlertConditionType;
import com.nurseli.nrsfinanceportal.domain.pricealert.PriceAlertStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceAlertEvaluatorTest {

    private PriceAlertEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new PriceAlertEvaluator();
    }

    @Test
    void priceGteTriggersWhenAtOrAboveThreshold() {
        PriceAlert alert = alert(PriceAlertConditionType.PRICE_GTE, new BigDecimal("350"));
        var snap = new PriceAlertMarketSnapshot(new BigDecimal("350.20"), null, true);
        assertTrue(evaluator.isTriggered(alert, snap));
    }

    @Test
    void priceGteDoesNotTriggerBelowThreshold() {
        PriceAlert alert = alert(PriceAlertConditionType.PRICE_GTE, new BigDecimal("350"));
        var snap = new PriceAlertMarketSnapshot(new BigDecimal("349.99"), null, true);
        assertFalse(evaluator.isTriggered(alert, snap));
    }

    @Test
    void priceLteTriggersWhenAtOrBelowThreshold() {
        PriceAlert alert = alert(PriceAlertConditionType.PRICE_LTE, new BigDecimal("3500"));
        var snap = new PriceAlertMarketSnapshot(new BigDecimal("3499"), null, true);
        assertTrue(evaluator.isTriggered(alert, snap));
    }

    @Test
    void changePctGteTriggersOnDailyMove() {
        PriceAlert alert = alert(PriceAlertConditionType.CHANGE_PCT_GTE, new BigDecimal("1.0"));
        var snap = new PriceAlertMarketSnapshot(new BigDecimal("40"), new BigDecimal("1.2"), true);
        assertTrue(evaluator.isTriggered(alert, snap));
    }

    @Test
    void changePctLteTriggersOnLargeDrop() {
        PriceAlert alert = alert(PriceAlertConditionType.CHANGE_PCT_LTE, new BigDecimal("-5"));
        var snap = new PriceAlertMarketSnapshot(new BigDecimal("90000"), new BigDecimal("-5.5"), true);
        assertTrue(evaluator.isTriggered(alert, snap));
    }

    @Test
    void doesNotTriggerWhenDataUnavailable() {
        PriceAlert alert = alert(PriceAlertConditionType.PRICE_GTE, new BigDecimal("350"));
        assertFalse(evaluator.isTriggered(alert, PriceAlertMarketSnapshot.unavailable()));
    }

    @Test
    void doesNotTriggerWhenInCooldownForOneShot() {
        PriceAlert alert = alert(PriceAlertConditionType.PRICE_GTE, new BigDecimal("350"));
        alert.setRepeatAlert(false);
        alert.setLastTriggeredAt(Instant.now().minus(1, ChronoUnit.HOURS));
        var snap = new PriceAlertMarketSnapshot(new BigDecimal("400"), null, true);
        assertFalse(evaluator.isTriggered(alert, snap));
    }

    private static PriceAlert alert(PriceAlertConditionType type, BigDecimal threshold) {
        PriceAlert a = new PriceAlert();
        a.setAssetType(AssetType.BIST);
        a.setSymbol("THYAO");
        a.setConditionType(type);
        a.setThreshold(threshold);
        a.setStatus(PriceAlertStatus.ACTIVE);
        a.setRepeatAlert(true);
        a.setCooldownHours(24);
        return a;
    }
}
