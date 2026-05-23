package com.nurseli.nrsfinanceportal.domain.portfolio;

/**
 * Portföy snapshot tetikleme nedeni.
 */
public enum SnapshotTriggerType {
    /** Zamanlanmış günlük kayıt */
    DAILY,
    /** Manuel pozisyon ekleme/güncelleme/silme sonrası */
    MANUAL
}
