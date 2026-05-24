package com.nurseli.marketdata.integration.support;

import com.nurseli.marketdata.config.BankRatesProperties;
import com.nurseli.marketdata.domain.bankfx.BankFxLatest;
import com.nurseli.marketdata.domain.deposit.DepositRateObservation;
import com.nurseli.marketdata.domain.inflation.InflationIndicatorType;
import com.nurseli.marketdata.domain.inflation.InflationIndexMonthlyEntity;
import com.nurseli.marketdata.domain.loan.LoanRateSubtype;
import com.nurseli.marketdata.domain.loan.LoanRateWeeklyObservation;
import com.nurseli.marketdata.domain.news.News;
import com.nurseli.marketdata.domain.news.NewsCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

public final class MarketdataIntegrationFixtures {

    public static final String CPI_SERIES_CODE = "TP_GENENDEKS_T1";
    public static final String PPI_SERIES_CODE = "TP_TUFE1YI_T1";
    public static final String LOAN_CONSUMER_SERIES = "TP_KTF10";
    public static final String DEPOSIT_TRY_1M_SERIES = "TP_TRY_MT01";

    private MarketdataIntegrationFixtures() {
    }

    public static News news(String externalId, String title, NewsCategory category) {
        LocalDateTime published = LocalDateTime.now().minusHours(1);
        return News.builder()
                .externalId(externalId)
                .title(title)
                .titleTr(title + " (TR)")
                .summary("Summary for " + title)
                .summaryTr("Özet: " + title)
                .source("IT")
                .url("https://example.com/news/" + externalId)
                .category(category)
                .publishedAt(published)
                .build();
    }

    public static BankFxLatest bankFxUsd(String bankCode, String bankName, BigDecimal buy, BigDecimal sell) {
        Instant now = Instant.now();
        return BankFxLatest.builder()
                .source(BankRatesProperties.SOURCE_DOVIZBORSA)
                .bankCode(bankCode)
                .bankName(bankName)
                .currency("USD")
                .buyPrice(buy)
                .sellPrice(sell)
                .changePct(new BigDecimal("0.1200"))
                .quoteTimeText("12:00")
                .fetchedAt(now)
                .updatedAt(now)
                .build();
    }

    public static InflationIndexMonthlyEntity inflationMonth(
            InflationIndicatorType type,
            String seriesCode,
            YearMonth month,
            BigDecimal indexValue
    ) {
        InflationIndexMonthlyEntity entity = new InflationIndexMonthlyEntity();
        entity.setIndicatorType(type);
        entity.setSeriesCode(seriesCode);
        entity.setObservationMonth(month.atDay(1));
        entity.setIndexValue(indexValue);
        entity.setMonthlyChangePct(new BigDecimal("1.50"));
        entity.setAnnualChangePct(new BigDecimal("45.00"));
        entity.setBaseYear(2003);
        LocalDateTime ts = LocalDateTime.now();
        entity.setCreatedAt(ts);
        entity.setUpdatedAt(ts);
        return entity;
    }

    public static LoanRateWeeklyObservation loanRate(
            LoanRateSubtype subType,
            LocalDate observedDate,
            BigDecimal ratePercent
    ) {
        LoanRateWeeklyObservation row = new LoanRateWeeklyObservation();
        row.setSeriesCode(LOAN_CONSUMER_SERIES);
        row.setSubType(subType);
        row.setObservedDate(observedDate);
        row.setRatePercent(ratePercent);
        LocalDateTime ts = LocalDateTime.now();
        row.setCreatedAt(ts);
        row.setUpdatedAt(ts);
        return row;
    }

    public static DepositRateObservation depositRate(
            LocalDate observationDate,
            BigDecimal rateValue
    ) {
        DepositRateObservation row = new DepositRateObservation();
        row.setSeriesCode(DEPOSIT_TRY_1M_SERIES);
        row.setCurrency("TRY");
        row.setTerm("1M");
        row.setObservationDate(observationDate);
        row.setRateValue(rateValue);
        LocalDateTime ts = LocalDateTime.now();
        row.setCreatedAt(ts);
        row.setUpdatedAt(ts);
        return row;
    }
}
