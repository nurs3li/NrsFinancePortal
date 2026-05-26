package com.nurseli.nrsfinanceportal.domain.portfolio;

/**
 * Alış/satış fiyatının kaynağı (USER_INPUT, market resolve vb.).
 */
public enum ManualPriceSource {
    USER_INPUT,
    MARKET_HISTORY_EXACT,
    MARKET_HISTORY_SAME_DAY_HOURLY,
    MARKET_HISTORY_PREVIOUS_CLOSE,
    MARKET_HISTORY_NEXT_CLOSE,
    NOT_RESOLVED
}
