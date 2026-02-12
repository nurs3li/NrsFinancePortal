package com.nurseli.nrsfinanceportal.integration.kafka;

public final class KafkaTopics {

    private KafkaTopics() {}

    public static final String TRANSACTION_CREATED =
            "finance.transaction.created";
    public static final String TRADE_CREATED = "finance.trade.created";
    public static final String SUSPICIOUS_DETECTED = "finance.suspicious.detected";
    public static final String TRANSACTION_REVERSED =
            "finance.transaction.reversed";
    public static final String WHALE_ALERT_TRIGGERED =
            "whale.alert.triggered";

}
