package com.nurseli.marketdata.application.bootstrap;

public enum BootstrapMode {
    /** Veri güncel; dış API çağrısı yok. */
    SKIP,
    /** Boş veya yetersiz DB — config `from` ile tam aralık. */
    FULL_SEED,
    /** DB'de max tarih var — yalnızca eksik kuyruk. */
    GAP_FILL
}
