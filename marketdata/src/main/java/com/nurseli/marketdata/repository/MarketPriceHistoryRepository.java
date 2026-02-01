package com.nurseli.marketdata.repository;

import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketPriceHistoryRepository
        extends JpaRepository<MarketPriceHistory, Long> {
}