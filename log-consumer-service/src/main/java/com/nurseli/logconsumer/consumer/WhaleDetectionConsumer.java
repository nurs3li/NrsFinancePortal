package com.nurseli.logconsumer.consumer;

import com.nurseli.logconsumer.event.TransactionCreatedEvent;
import com.nurseli.logconsumer.whale.RedisWhaleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhaleDetectionConsumer {

    private final RedisWhaleRepository whaleRepository;

    @KafkaListener(
            topics = "finance.transaction.created",
            groupId = "whale-detector"
    )
    public void consume(TransactionCreatedEvent event) {

        Long userId = event.userId();

        whaleRepository.incrementHourlyCount(userId);
        whaleRepository.addDailyVolume(userId, event.amount());
        whaleRepository.updateMaxTransaction(userId, event.amount());

        log.info(
                "[WHALE-DETECTOR] userId={} amount={}",
                userId,
                event.amount()
        );
    }
}
