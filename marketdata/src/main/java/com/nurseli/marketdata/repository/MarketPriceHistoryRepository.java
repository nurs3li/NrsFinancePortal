package com.nurseli.marketdata.repository;

import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MarketPriceHistoryRepository
        extends JpaRepository<MarketPriceHistory, Long> {

    // =====================================================
    // 🔹 LATEST – SINGLE SYMBOL
    // =====================================================
    Optional<MarketPriceHistory>
    findTopBySymbolOrderByTimestampDesc(String symbol);

    // =====================================================
    // 🔹 LATEST – ALL SYMBOLS BY SOURCE (CRYPTO / FX / FUND)
    // =====================================================
    @Query("""
        SELECT m
        FROM MarketPriceHistory m
        WHERE m.source = :source
          AND m.timestamp = (
              SELECT MAX(m2.timestamp)
              FROM MarketPriceHistory m2
              WHERE m2.symbol = m.symbol
          )
        ORDER BY m.symbol
    """)
    List<MarketPriceHistory> findLatestBySource(
            @Param("source") String source
    );

    // =====================================================
    // 🔹 HISTORY – TIME BUCKET (1 MINUTE)
    // =====================================================
    @Query(value = """
        SELECT
            date_trunc('minute', timestamp) AS timestamp,
            AVG(buy_price)                  AS buyPrice,
            AVG(sell_price)                AS sellPrice
        FROM market_price_history
        WHERE symbol = :symbol
          AND timestamp BETWEEN :start AND :end
        GROUP BY timestamp
        ORDER BY timestamp
        """, nativeQuery = true)
    List<MarketPriceBucketView> findBucketedHistory(
            @Param("symbol") String symbol,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    List<MarketPriceHistory> findBySymbolAndTimestampBetweenOrderByTimestampAsc(
            String symbol,
            LocalDateTime start,
            LocalDateTime end
    );

    // =====================================================
    // 🔹 BACKFILL – GÜN BAZINDA VAR MI?
    // =====================================================
    @Query("""
        SELECT COUNT(m) > 0
        FROM MarketPriceHistory m
        WHERE m.symbol = :symbol
          AND m.timestamp >= :start
          AND m.timestamp < :end
    """)
    boolean existsForDay(
            @Param("symbol") String symbol,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}