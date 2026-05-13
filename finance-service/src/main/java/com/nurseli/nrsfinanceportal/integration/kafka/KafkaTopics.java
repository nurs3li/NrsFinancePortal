package com.nurseli.nrsfinanceportal.integration.kafka;

public final class KafkaTopics {

    private KafkaTopics() {}

    public static final String NOTIFICATION_EVENTS = "notification-events";
    /** Log4j2 appender tarafından kullanılır (log4j2-spring.xml) */
    public static final String APPLICATION_LOGS = "application-logs";
}
