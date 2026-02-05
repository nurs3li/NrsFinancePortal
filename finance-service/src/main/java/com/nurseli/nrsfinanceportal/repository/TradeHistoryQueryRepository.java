package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.common.dto.TradeHistoryDto;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.trade.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface TradeHistoryQueryRepository extends Repository<Trade, Long> {

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
    """)
    Page<TradeHistoryDto> findTradeHistory(
            @Param("userId") Long userId,
            @Param("assetType") AssetType assetType,
            Pageable pageable
    );
}
