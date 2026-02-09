package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.common.dto.TradeHistoryDto;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.trade.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TradeHistoryQueryRepository extends JpaRepository<Trade, Long> {

    /**
     * 🔹 MEVCUT METOT (GERİYE UYUMLULUK İÇİN KORUNUYOR)
     */
    @Query("""
        select new com.nurseli.nrsfinanceportal.common.dto.TradeHistoryDto(
            t.id,
            t.tradeType,
            t.assetType,
            t.symbol,
            t.quantity,
            tx.amount,
            tx.balanceAfter,
            t.createdAt
        )
        from Trade t
        join Transaction tx on tx.id = t.transactionId
        where t.user.id = :userId
          and (:assetType is null or t.assetType = :assetType)
        order by t.createdAt desc
    """)
    Page<TradeHistoryDto> findTradeHistory(
            @Param("userId") Long userId,
            @Param("assetType") AssetType assetType,
            Pageable pageable
    );

    /**
     * 🔹 FAZ 2.1 – KEYSET PAGINATION (INFINITE SCROLL)
     *
     * Cursor = (createdAt, tradeId)
     */
    @Query("""
        select new com.nurseli.nrsfinanceportal.common.dto.TradeHistoryDto(
            t.id,
            t.tradeType,
            t.assetType,
            t.symbol,
            t.quantity,
            tx.amount,
            tx.balanceAfter,
            t.createdAt
        )
        from Trade t
        join Transaction tx on tx.id = t.transactionId
        where t.user.id = :userId
          and (
                :cursorCreatedAt is null
                or (t.createdAt < :cursorCreatedAt)
                or (t.createdAt = :cursorCreatedAt and t.id < :cursorTradeId)
          )
        order by t.createdAt desc, t.id desc
    """)
    List<TradeHistoryDto> findTradeHistoryAfterCursor(
            @Param("userId") Long userId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorTradeId") Long cursorTradeId,
            Pageable pageable
    );
}
