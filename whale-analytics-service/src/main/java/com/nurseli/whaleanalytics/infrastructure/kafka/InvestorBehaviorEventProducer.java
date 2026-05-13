package com.nurseli.whaleanalytics.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.whaleanalytics.config.WhaleKafkaTopicProperties;
import com.nurseli.whaleanalytics.event.investment.InvestorBehaviorUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvestorBehaviorEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final WhaleKafkaTopicProperties topicProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(InvestorBehaviorUpdatedEvent event) {
        try {
            kafkaTemplate.send(topicProperties.getInvestorBehaviorUpdated(), String.valueOf(event.userId()), event);
            writeDashboardWhaleState(event);
            log.info("[KAFKA][INVESTOR_BEHAVIOR] topic={} userId={} level={} score={}",
                    topicProperties.getInvestorBehaviorUpdated(), event.userId(), event.investorLevel(), event.portfolioImpactScore());
        } catch (Exception e) {
            log.error("[KAFKA][INVESTOR_BEHAVIOR] publish failed userId={}", event.userId(), e);
        }
    }

    /**
     * finance-service {@code WhaleStateCacheService} ile uyumlu özet (dashboard kartı).
     */
    private void writeDashboardWhaleState(InvestorBehaviorUpdatedEvent event) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("level", event.investorLevel().name());
        body.put("impactScore", event.portfolioImpactScore());
        body.put("triggeredAt", event.occurredAt().toString());
        stringRedisTemplate.opsForValue().set("whale:last:" + event.userId(), objectMapper.writeValueAsString(body));
    }
}
