package com.nurseli.nrsfinanceportal.integration.listener;

import com.nurseli.nrsfinanceportal.domain.event.TransactionCreatedEvent;
import com.nurseli.nrsfinanceportal.domain.event.TransactionReversedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TransactionEventLogger {

    @EventListener
    public void onTransactionCreated(TransactionCreatedEvent event) {
        log.info(
                "[DOMAIN EVENT] Transaction CREATED | txId={} | accountId={} | userId={} | type={} | amount={} | balanceAfter={}",
                event.transactionId(),
                event.accountId(),
                event.userId(),
                event.type(),
                event.amount(),
                event.balanceAfter()
        );
    }

    @EventListener
    public void onTransactionReversed(TransactionReversedEvent event) {
        log.info(
                "[DOMAIN EVENT] Transaction REVERSED | reversalTxId={} | originalTxId={} | accountId={} | adminUserId={} | amount={} | balanceAfter={}",
                event.reversalTransactionId(),
                event.originalTransactionId(),
                event.accountId(),
                event.adminUserId(),
                event.amount(),
                event.balanceAfter()
        );
    }
}
