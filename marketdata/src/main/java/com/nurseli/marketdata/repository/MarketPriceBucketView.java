package com.nurseli.marketdata.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface MarketPriceBucketView {

    LocalDateTime getTimestamp();

    BigDecimal getBuyPrice();

    BigDecimal getSellPrice();
}