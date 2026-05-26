package com.nurseli.marketdata.infrastructure.persistence;

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
    Optional<MarketPriceHistory>
    findTopBySymbolOrderByTimestampAsc(String symbol);

    boolean existsBySymbolAndTimestamp(String symbol, LocalDateTime timestamp);

    // =====================================================
    // 🔹 LATEST – ALL SYMBOLS BY SOURCE (CRYPTO / FX / FUND)
    // =====================================================
    /**
     * Kaynak başına sembolün en son satırı (PostgreSQL).
     * Eski JPQL korelasyonlu alt sorgu büyük tabloda çok yavaşlatıyordu.
     */
    @Query(
            value = """
                    SELECT DISTINCT ON (symbol) *
                    FROM market_price_history
                    WHERE source = :source
                    ORDER BY symbol, timestamp DESC, id DESC
                    """,
            nativeQuery = true
    )
    List<MarketPriceHistory> findLatestBySource(@Param("source") String source);

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

    @Query("""
        SELECT COUNT(m)
        FROM MarketPriceHistory m
        WHERE m.symbol = :symbol
          AND m.timestamp >= :start
          AND m.timestamp < :end
    """)
    long countForDay(
            @Param("symbol") String symbol,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
        SELECT COUNT(m)
        FROM MarketPriceHistory m
        WHERE m.symbol = :symbol
          AND m.timestamp >= :start
          AND m.timestamp < :endExclusive
    """)
    long countBySymbolAndTimestampRange(
            @Param("symbol") String symbol,
            @Param("start") LocalDateTime start,
            @Param("endExclusive") LocalDateTime endExclusive
    );

    @Query(
            value = """
                    SELECT COUNT(DISTINCT CAST(timestamp AS DATE))
                    FROM market_price_history
                    WHERE symbol = :symbol
                      AND timestamp >= :start
                      AND timestamp < :endExclusive
                    """,
            nativeQuery = true
    )
    long countDistinctTradeDaysBySymbolAndTimestampRange(
            @Param("symbol") String symbol,
            @Param("start") LocalDateTime start,
            @Param("endExclusive") LocalDateTime endExclusive
    );

    // --- BIST günlük (HisseTekil) — IS_YATIRIM kaynaklı satırlar ---

    Optional<MarketPriceHistory> findBySymbolAndSourceAndTimestamp(
            String symbol, String source, LocalDateTime timestamp);

    Optional<MarketPriceHistory> findTopBySymbolAndSourceOrderByTimestampDesc(String symbol, String source);

    Optional<MarketPriceHistory> findTopBySymbolAndSourceOrderByTimestampAsc(String symbol, String source);

    List<MarketPriceHistory> findTop2BySymbolAndSourceOrderByTimestampDesc(String symbol, String source);

    long countBySource(String source);

    long countBySymbolAndSource(String symbol, String source);

    @Query("SELECT MIN(m.timestamp) FROM MarketPriceHistory m WHERE m.source = :source")
    Optional<LocalDateTime> findMinTimestampBySource(@Param("source") String source);

    @Query("SELECT MAX(m.timestamp) FROM MarketPriceHistory m WHERE m.source = :source")
    Optional<LocalDateTime> findMaxTimestampBySource(@Param("source") String source);

    @Query(
            """
                    SELECT m FROM MarketPriceHistory m
                    WHERE m.symbol = :symbol AND m.source = :source
                      AND m.timestamp >= :start AND m.timestamp < :endExclusive
                    ORDER BY m.timestamp ASC
                    """)
    List<MarketPriceHistory> findBySymbolAndSourceAndTimestampRange(
            @Param("symbol") String symbol,
            @Param("source") String source,
            @Param("start") LocalDateTime start,
            @Param("endExclusive") LocalDateTime endExclusive);

    @Query(
            value = """
                    SELECT COUNT(DISTINCT CAST(timestamp AS DATE))
                    FROM market_price_history
                    WHERE symbol = :symbol
                      AND source = :source
                      AND timestamp >= :start
                      AND timestamp < :endExclusive
                    """,
            nativeQuery = true
    )
    long countDistinctTradeDaysBySymbolAndSourceAndTimestampRange(
            @Param("symbol") String symbol,
            @Param("source") String source,
            @Param("start") LocalDateTime start,
            @Param("endExclusive") LocalDateTime endExclusive
    );

    @Query(
            """
                    SELECT m FROM MarketPriceHistory m
                    WHERE m.symbol IN :symbols AND m.source = :source
                      AND m.timestamp >= :start AND m.timestamp < :endExclusive
                    ORDER BY m.symbol ASC, m.timestamp ASC
                    """)
    List<MarketPriceHistory> findBySymbolsAndSourceAndTimestampRange(
            @Param("symbols") List<String> symbols,
            @Param("source") String source,
            @Param("start") LocalDateTime start,
            @Param("endExclusive") LocalDateTime endExclusive);
}