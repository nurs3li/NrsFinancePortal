package com.nurseli.nrsfinanceportal.domain.portfolio;

public enum SnapshotTriggerType {
    /** Zamanlanmış günlük kayıt */
    DAILY,
    /** Borsa/emir işlemi sonrası (Kafka trade.created tüketimi) */
    TRADE,
    /** Manuel pozisyon ekleme/güncelleme/silme sonrası */
    MANUAL
}
