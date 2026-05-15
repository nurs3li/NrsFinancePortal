package com.nurseli.marketdata.config;

/**
 * {@code market.evds.series} YAML haritasındaki mantıksal gösterge anahtarları.
 * EVDS kolon kodları burada sabitlenmez; yalnızca yapılandırma anahtarı olarak kullanılır.
 */
public final class EvdsSeriesLogicalNames {

    public static final String CPI_TR_INDEX = "CPI_TR_INDEX";
    /** Yurt içi üretici fiyat endeksi (Yİ-ÜFE), aylık, örn. TP_TUFE1YI_T1. */
    public static final String PPI_TR_INDEX = "PPI_TR_INDEX";
    public static final String TCMB_WEIGHTED_AVG_FUNDING_COST_TR = "TCMB_WEIGHTED_AVG_FUNDING_COST_TR";
    public static final String POLICY_RATE_TR = "POLICY_RATE_TR";
    public static final String LOAN_RATE_CONSUMER_TRY_WEEKLY = "LOAN_RATE_CONSUMER_TRY_WEEKLY";
    public static final String LOAN_RATE_VEHICLE_TRY_WEEKLY = "LOAN_RATE_VEHICLE_TRY_WEEKLY";
    public static final String LOAN_RATE_HOUSING_TRY_WEEKLY = "LOAN_RATE_HOUSING_TRY_WEEKLY";
    public static final String LOAN_RATE_COMMERCIAL_TRY_WEEKLY = "LOAN_RATE_COMMERCIAL_TRY_WEEKLY";

    /** Mevduat faiz oranları (EVDS akım %), haftalık — {@code market.evds.series} + {@code EVDS_SERIES_DEPOSIT_RATE_*}. */
    public static final String DEPOSIT_RATE_TRY_1M_WEEKLY = "DEPOSIT_RATE_TRY_1M_WEEKLY";
    public static final String DEPOSIT_RATE_TRY_3M_WEEKLY = "DEPOSIT_RATE_TRY_3M_WEEKLY";
    public static final String DEPOSIT_RATE_TRY_6M_WEEKLY = "DEPOSIT_RATE_TRY_6M_WEEKLY";
    public static final String DEPOSIT_RATE_TRY_1Y_WEEKLY = "DEPOSIT_RATE_TRY_1Y_WEEKLY";
    public static final String DEPOSIT_RATE_TRY_GT1Y_WEEKLY = "DEPOSIT_RATE_TRY_GT1Y_WEEKLY";
    public static final String DEPOSIT_RATE_EUR_1M_WEEKLY = "DEPOSIT_RATE_EUR_1M_WEEKLY";
    public static final String DEPOSIT_RATE_EUR_3M_WEEKLY = "DEPOSIT_RATE_EUR_3M_WEEKLY";
    public static final String DEPOSIT_RATE_EUR_6M_WEEKLY = "DEPOSIT_RATE_EUR_6M_WEEKLY";
    public static final String DEPOSIT_RATE_EUR_1Y_WEEKLY = "DEPOSIT_RATE_EUR_1Y_WEEKLY";
    public static final String DEPOSIT_RATE_USD_1M_WEEKLY = "DEPOSIT_RATE_USD_1M_WEEKLY";
    public static final String DEPOSIT_RATE_USD_3M_WEEKLY = "DEPOSIT_RATE_USD_3M_WEEKLY";
    public static final String DEPOSIT_RATE_USD_6M_WEEKLY = "DEPOSIT_RATE_USD_6M_WEEKLY";
    public static final String DEPOSIT_RATE_USD_1Y_WEEKLY = "DEPOSIT_RATE_USD_1Y_WEEKLY";

    private EvdsSeriesLogicalNames() {}
}
