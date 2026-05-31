package com.nurseli.nrsfinanceportal.infrastructure.persistence;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualSymbolDailyClose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ManualSymbolDailyCloseRepository extends JpaRepository<ManualSymbolDailyClose, Long> {

    @Query("""
            SELECT c FROM ManualSymbolDailyClose c
            WHERE c.user.id = :userId
              AND c.assetType = :assetType
              AND c.symbol = :symbol
              AND c.tradeDate >= :from
              AND c.tradeDate <= :to
            ORDER BY c.tradeDate ASC
            """)
    List<ManualSymbolDailyClose> findRange(
            @Param("userId") Long userId,
            @Param("assetType") AssetType assetType,
            @Param("symbol") String symbol,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    @Query("""
            SELECT MAX(c.tradeDate) FROM ManualSymbolDailyClose c
            WHERE c.user.id = :userId
              AND c.assetType = :assetType
              AND c.symbol = :symbol
            """)
    Optional<LocalDate> findLatestTradeDate(
            @Param("userId") Long userId,
            @Param("assetType") AssetType assetType,
            @Param("symbol") String symbol
    );

    @Query("""
            SELECT DISTINCT c.assetType, c.symbol FROM ManualSymbolDailyClose c
            WHERE c.user.id = :userId
            """)
    List<Object[]> findDistinctSymbols(@Param("userId") Long userId);

    @Modifying
    @Query(value = """
            INSERT INTO manual_symbol_daily_close
                (user_id, asset_type, symbol, trade_date, close_try, price_source, fetched_at)
            VALUES (:userId, :assetType, :symbol, :tradeDate, :closeTry, :priceSource, :fetchedAt)
            ON CONFLICT (user_id, asset_type, symbol, trade_date)
            DO UPDATE SET
                close_try = EXCLUDED.close_try,
                price_source = EXCLUDED.price_source,
                fetched_at = EXCLUDED.fetched_at
            """, nativeQuery = true)
    void upsertNative(
            @Param("userId") Long userId,
            @Param("assetType") String assetType,
            @Param("symbol") String symbol,
            @Param("tradeDate") LocalDate tradeDate,
            @Param("closeTry") BigDecimal closeTry,
            @Param("priceSource") String priceSource,
            @Param("fetchedAt") Instant fetchedAt
    );

    @Modifying
    @Query("DELETE FROM ManualSymbolDailyClose c WHERE c.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
