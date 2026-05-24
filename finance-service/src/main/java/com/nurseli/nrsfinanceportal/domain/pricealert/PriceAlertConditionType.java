package com.nurseli.nrsfinanceportal.domain.pricealert;

/**
 * Alarm tetik koşulu (fiyat üstü/altı, yüzde değişim vb.).
 */
public enum PriceAlertConditionType {
    PRICE_GTE,
    PRICE_LTE,
    CHANGE_PCT_GTE,
    CHANGE_PCT_LTE
}
