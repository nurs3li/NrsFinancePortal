package com.nurseli.nrsfinanceportal.config;

/**
 * Kafka / OpenSearch audit alanları — Log4j {@code ThreadContext} ve SLF4J {@code MDC} ile aynı anahtarlar.
 */
public final class AuditContextMdcKeys {

    public static final String USER_ID = "userId";
    /** Örn. TRADE, PROFILE, ADMIN — {@link AuditContextMdcFilter} üretir */
    public static final String ACTION_TYPE = "actionType";
    public static final String USERNAME = "username";

    private AuditContextMdcKeys() {}
}
