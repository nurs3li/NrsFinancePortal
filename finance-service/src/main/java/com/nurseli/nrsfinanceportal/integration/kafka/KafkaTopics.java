package com.nurseli.nrsfinanceportal.integration.kafka;

public final class KafkaTopics {

    private KafkaTopics() {}

    public static final String TRANSACTION_CREATED =
            "finance.transaction.created";

    public static final String TRANSACTION_REVERSED =
            "finance.transaction.reversed";
}
