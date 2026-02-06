package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.TradeHistoryDto;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.repository.TradeHistoryQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
@Service
@RequiredArgsConstructor
public class TradeHistoryQueryService {

    private final TradeHistoryQueryRepository repository;

    public Page<TradeHistoryDto> getTradeHistory(
            Long userId,
            AssetType assetType,
            Pageable pageable
    ) {
        return repository.findTradeHistory(userId, assetType, pageable);
    }
}
