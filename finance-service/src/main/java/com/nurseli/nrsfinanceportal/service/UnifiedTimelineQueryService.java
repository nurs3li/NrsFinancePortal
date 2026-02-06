package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.TimelineType;
import com.nurseli.nrsfinanceportal.common.dto.TradeHistoryDto;
import com.nurseli.nrsfinanceportal.common.dto.UnifiedTimelineDto;
import com.nurseli.nrsfinanceportal.domain.whale.WhaleHistory;
import com.nurseli.nrsfinanceportal.domain.whale.WhaleLevel;
import com.nurseli.nrsfinanceportal.repository.TradeHistoryQueryRepository;
import com.nurseli.nrsfinanceportal.repository.WhaleHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class UnifiedTimelineQueryService {

    private final TradeHistoryQueryRepository tradeRepository;
    private final WhaleHistoryRepository whaleRepository;

    public List<UnifiedTimelineDto> getTimeline(Long userId) {

        // 1️⃣ Whale history (DESC – en güncel üstte)
        List<WhaleHistory> whaleHistories =
                whaleRepository.findByUserIdOrderByTriggeredAtDesc(userId);

        // 2️⃣ Trade timeline
        return tradeRepository
                .findTradeHistory(userId, null, Pageable.unpaged())
                .stream()
                .map(trade -> {

                    // 3️⃣ Bu trade zamanından ÖNCE oluşmuş en yakın whale
                    WhaleHistory relatedWhale =
                            whaleHistories.stream()
                                    .filter(w ->
                                            !w.getTriggeredAt()
                                                    .isAfter(trade.tradedAt()))
                                    .findFirst()
                                    .orElse(null);

                    return new UnifiedTimelineDto(
                            TimelineType.TRADE,
                            trade.tradedAt(),

                            trade.tradeId(),
                            trade.tradeType(),
                            trade.assetType(),
                            trade.symbol(),
                            trade.quantity(),
                            trade.totalTry(),
                            trade.balanceAfter(),

                            relatedWhale != null
                                    ? relatedWhale.getWhaleLevel()
                                    : null,

                            // 🔥 ARTIK NULL DEĞİL - WhaleHistory içinden skoru alıyoruz
                            relatedWhale != null && relatedWhale.getImpactScore() != null
                                    ? BigDecimal.valueOf(relatedWhale.getImpactScore())
                                    : BigDecimal.ZERO
                    );
                })
                .sorted(Comparator.comparing(UnifiedTimelineDto::occurredAt).reversed())
                .toList();
    }
}