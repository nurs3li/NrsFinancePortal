package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.TimelineType;
import com.nurseli.nrsfinanceportal.common.dto.TradeHistoryDto;
import com.nurseli.nrsfinanceportal.common.dto.UnifiedTimelineDto;
import com.nurseli.nrsfinanceportal.domain.whale.WhaleHistory;
import com.nurseli.nrsfinanceportal.repository.TradeHistoryQueryRepository;
import com.nurseli.nrsfinanceportal.repository.WhaleHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UnifiedTimelineQueryService {

    private final TradeHistoryQueryRepository tradeRepository;
    private final WhaleHistoryRepository whaleRepository;

    /**
     * GERİYE UYUMLU METOT
     */
    public List<UnifiedTimelineDto> getTimelinePage(Long userId) {
        return getTimelinePage(userId, null, null, 50);
    }

    /**
     *   CURSOR / KEYSET PAGINATION
     *   SADECE EN GÜNCEL WHALE STATE
     */
    public List<UnifiedTimelineDto> getTimelinePage(
            Long userId,
            Instant cursorOccurredAt,
            Long cursorTradeId,
            int size
    ) {

        Pageable pageable = PageRequest.of(0, size);

        List<UnifiedTimelineDto> result = new ArrayList<>();

        /* =========================
           EN GÜNCEL WHALE STATE
           ========================= */
        whaleRepository
                .findByUserIdOrderByTriggeredAtDesc(userId)
                .stream()
                .findFirst() //  SADECE EN GÜNCEL
                .ifPresent(whale ->
                        result.add(mapWhaleToTimeline(whale))
                );

        /* =========================
          TRADE TIMELINE
           ========================= */
        List<TradeHistoryDto> trades =
                cursorOccurredAt == null
                        ? tradeRepository
                        .findTradeHistory(userId, null, pageable)
                        .getContent()
                        : tradeRepository
                        .findTradeHistoryAfterCursor(
                                userId,
                                cursorOccurredAt,
                                cursorTradeId,
                                pageable
                        );

        trades.stream()
                .map(trade -> {

                    WhaleHistory relatedWhale =
                            whaleRepository
                                    .findByUserIdOrderByTriggeredAtDesc(userId)
                                    .stream()
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

                            relatedWhale != null && relatedWhale.getImpactScore() != null
                                    ? BigDecimal.valueOf(relatedWhale.getImpactScore())
                                    : BigDecimal.ZERO
                    );
                })
                .forEach(result::add);

        /* =========================
           GLOBAL SORT
           ========================= */
        return result.stream()
                .sorted(Comparator.comparing(UnifiedTimelineDto::occurredAt).reversed())
                .toList();
    }

    /* =========================
       WHALE → TIMELINE ITEM
       ========================= */
    private UnifiedTimelineDto mapWhaleToTimeline(WhaleHistory whale) {

        return new UnifiedTimelineDto(
                TimelineType.WHALE,
                whale.getTriggeredAt(),

                null,
                null,
                null,
                null,
                null,
                null,
                null,

                whale.getWhaleLevel(),
                whale.getImpactScore() != null
                        ? BigDecimal.valueOf(whale.getImpactScore())
                        : BigDecimal.ZERO
        );
    }
}
