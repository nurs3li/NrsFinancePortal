package com.nurseli.nrsfinanceportal.audit;

import com.nurseli.nrsfinanceportal.domain.event.TransactionCreatedEvent;
import com.nurseli.nrsfinanceportal.domain.event.TransactionReversedEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import static com.nurseli.nrsfinanceportal.config.CorrelationIdFilter.CORRELATION_ID_MDC_KEY;

/**
 * Audit amaçlı, domain event'ler üzerinden ayrı bir log hattı.
 * - Üretim ortamında bu loglar genelde ayrı bir index / storage'a yönlendirilir.
 */
@Slf4j
@Service
public class AuditLogService {

    @EventListener
    public void onTransactionCreated(TransactionCreatedEvent event) {

        String correlationId = MDC.get(CORRELATION_ID_MDC_KEY);

        log.info(
                "[AUDIT][TX_CREATED] correlationId={} txId={} accountId={} userId={} type={} amount={} balanceAfter={} occurredAt={}",
                correlationId,
                event.transactionId(),
                event.accountId(),
                event.userId(),
                event.type(),
                event.amount(),
                event.balanceAfter(),
                event.occurredAt()
        );
    }

    @EventListener
    public void onTransactionReversed(TransactionReversedEvent event) {

        String correlationId = MDC.get(CORRELATION_ID_MDC_KEY);

        log.info(
                "[AUDIT][TX_REVERSED] correlationId={} reversalTxId={} originalTxId={} accountId={} adminUserId={} amount={} balanceAfter={} occurredAt={}",
                correlationId,
                event.reversalTransactionId(),
                event.originalTransactionId(),
                event.accountId(),
                event.adminUserId(),
                event.amount(),
                event.balanceAfter(),
                event.occurredAt()
        );
    }
}