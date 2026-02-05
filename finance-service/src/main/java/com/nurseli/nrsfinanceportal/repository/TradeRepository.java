package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.trade.Trade;
import com.nurseli.nrsfinanceportal.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByUserOrderByCreatedAtDesc(User user);
}
