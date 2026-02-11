package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.trade.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    @Query("""
        select count(t)
        from Trade t
        where t.user.id = :userId
          and t.createdAt >= :since
    """)
    int countTradesSince(Long userId, Instant since);

    @Query("""
        select max(t.createdAt)
        from Trade t
        where t.user.id = :userId
    """)
    Instant findLastTradeTime(Long userId);
}
