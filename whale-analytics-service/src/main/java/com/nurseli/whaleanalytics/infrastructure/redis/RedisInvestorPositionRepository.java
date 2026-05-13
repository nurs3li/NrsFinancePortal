package com.nurseli.whaleanalytics.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.whaleanalytics.domain.investor.InvestorPositionSnapshot;
import com.nurseli.whaleanalytics.event.investment.InvestmentPositionClosedEvent;
import com.nurseli.whaleanalytics.event.investment.InvestmentPositionCreatedEvent;
import com.nurseli.whaleanalytics.event.investment.InvestmentPositionUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisInvestorPositionRepository {

    private static final String POS_HASH = "investor:user:%d:positions";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void upsertFromCreated(InvestmentPositionCreatedEvent e) {
        if (!validate(e.eventId(), e.userId(), e.positionId())) {
            return;
        }
        InvestorPositionSnapshot snap = new InvestorPositionSnapshot(
                e.positionId(),
                e.userId(),
                e.assetType(),
                e.symbol(),
                e.quantity(),
                e.status(),
                e.investedAmountTry(),
                e.currentValueTry(),
                null,
                e.nominalProfitTry(),
                e.realProfitTry(),
                e.occurredAt()
        );
        write(snap);
    }

    public void upsertFromUpdated(InvestmentPositionUpdatedEvent e) {
        if (!validate(e.eventId(), e.userId(), e.positionId())) {
            return;
        }
        InvestorPositionSnapshot snap = new InvestorPositionSnapshot(
                e.positionId(),
                e.userId(),
                e.assetType(),
                e.symbol(),
                e.quantity(),
                e.status(),
                e.investedAmountTry(),
                e.currentValueTry(),
                null,
                e.nominalProfitTry(),
                e.realProfitTry(),
                e.occurredAt()
        );
        write(snap);
    }

    public void upsertFromClosed(InvestmentPositionClosedEvent e) {
        if (!validate(e.eventId(), e.userId(), e.positionId())) {
            return;
        }
        InvestorPositionSnapshot snap = new InvestorPositionSnapshot(
                e.positionId(),
                e.userId(),
                e.assetType(),
                e.symbol(),
                e.quantity(),
                e.status(),
                e.investedAmountTry(),
                null,
                e.sellValueTry(),
                e.realizedProfitTry(),
                e.realProfitTry(),
                e.occurredAt()
        );
        write(snap);
    }

    public List<InvestorPositionSnapshot> findAllForUser(long userId) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(key(userId));
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<InvestorPositionSnapshot> out = new ArrayList<>();
        for (Object v : raw.values()) {
            if (v == null) {
                continue;
            }
            try {
                out.add(objectMapper.readValue(v.toString(), InvestorPositionSnapshot.class));
            } catch (Exception ex) {
                log.warn("[INVESTOR_REDIS] deserialize failed userId={}: {}", userId, ex.getMessage());
            }
        }
        return out;
    }

    private void write(InvestorPositionSnapshot snap) {
        try {
            String json = objectMapper.writeValueAsString(snap);
            redisTemplate.opsForHash().put(key(snap.userId()), String.valueOf(snap.positionId()), json);
        } catch (Exception ex) {
            log.error("[INVESTOR_REDIS] write failed userId={} positionId={}", snap.userId(), snap.positionId(), ex);
        }
    }

    private static boolean validate(String eventId, Long userId, Long positionId) {
        if (eventId == null || eventId.isBlank() || userId == null || positionId == null) {
            return false;
        }
        return true;
    }

    private static String key(long userId) {
        return POS_HASH.formatted(userId);
    }
}
