package com.nurseli.marketdata.application.bist;

import com.nurseli.marketdata.infrastructure.bist.BistProviderSource;

import java.time.ZoneId;

public final class BistEquityDailyConstants {

    /** Günlük HisseTekil satırlarında {@code market_price_history.source} değeri */
    public static final String HISTORY_SOURCE = BistProviderSource.IS_YATIRIM.name();

    public static final ZoneId IST = ZoneId.of("Europe/Istanbul");

    private BistEquityDailyConstants() {}
}
