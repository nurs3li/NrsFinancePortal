package com.nurseli.marketdata.domain.viop;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "mds_viop_snapshot")
public class ViopSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_code", nullable = false, length = 64)
    private String contractCode;

    @Column(nullable = false, length = 64)
    private String underlying;

    @Column(name = "asset_class", nullable = false, length = 32)
    private String assetClass;

    @Column(nullable = false, length = 64)
    private String segment;

    @Column(name = "update_date", nullable = false)
    private LocalDateTime updateDate;

    @Column(precision = 24, scale = 8)
    private BigDecimal bid;

    @Column(precision = 24, scale = 8)
    private BigDecimal ask;

    @Column(precision = 24, scale = 8)
    private BigDecimal low;

    @Column(precision = 24, scale = 8)
    private BigDecimal high;

    @Column(precision = 24, scale = 8)
    private BigDecimal last;

    @Column(name = "day_close", precision = 24, scale = 8)
    private BigDecimal dayClose;

    @Column(name = "open_price", precision = 24, scale = 8)
    private BigDecimal openPrice;

    @Column(name = "change_amount", precision = 24, scale = 8)
    private BigDecimal changeAmount;

    @Column(name = "change_percent", precision = 12, scale = 6)
    private BigDecimal changePercent;

    @Column(precision = 24, scale = 8)
    private BigDecimal quantity;

    @Column(precision = 24, scale = 8)
    private BigDecimal volume;

    @Column(precision = 24, scale = 8)
    private BigDecimal settlement;

    @Column(name = "pre_settlement", precision = 24, scale = 8)
    private BigDecimal preSettlement;

    @Column(name = "limit_up", precision = 24, scale = 8)
    private BigDecimal limitUp;

    @Column(name = "limit_down", precision = 24, scale = 8)
    private BigDecimal limitDown;

    @Column(name = "price_step", precision = 24, scale = 8)
    private BigDecimal priceStep;

    @Column(name = "initial_margin", precision = 24, scale = 8)
    private BigDecimal initialMargin;

    @Column(name = "week_low", precision = 24, scale = 8)
    private BigDecimal weekLow;

    @Column(name = "week_high", precision = 24, scale = 8)
    private BigDecimal weekHigh;

    @Column(name = "week_close", precision = 24, scale = 8)
    private BigDecimal weekClose;

    @Column(name = "month_low", precision = 24, scale = 8)
    private BigDecimal monthLow;

    @Column(name = "month_high", precision = 24, scale = 8)
    private BigDecimal monthHigh;

    @Column(name = "month_close", precision = 24, scale = 8)
    private BigDecimal monthClose;

    @Column(name = "year_close", precision = 24, scale = 8)
    private BigDecimal yearClose;

    @Column(name = "prev_year_close", precision = 24, scale = 8)
    private BigDecimal prevYearClose;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public String getContractCode() {
        return contractCode;
    }

    public void setContractCode(String contractCode) {
        this.contractCode = contractCode;
    }

    public String getUnderlying() {
        return underlying;
    }

    public void setUnderlying(String underlying) {
        this.underlying = underlying;
    }

    public String getAssetClass() {
        return assetClass;
    }

    public void setAssetClass(String assetClass) {
        this.assetClass = assetClass;
    }

    public String getSegment() {
        return segment;
    }

    public void setSegment(String segment) {
        this.segment = segment;
    }

    public LocalDateTime getUpdateDate() {
        return updateDate;
    }

    public void setUpdateDate(LocalDateTime updateDate) {
        this.updateDate = updateDate;
    }

    public BigDecimal getBid() {
        return bid;
    }

    public void setBid(BigDecimal bid) {
        this.bid = bid;
    }

    public BigDecimal getAsk() {
        return ask;
    }

    public void setAsk(BigDecimal ask) {
        this.ask = ask;
    }

    public BigDecimal getLow() {
        return low;
    }

    public void setLow(BigDecimal low) {
        this.low = low;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public void setHigh(BigDecimal high) {
        this.high = high;
    }

    public BigDecimal getLast() {
        return last;
    }

    public void setLast(BigDecimal last) {
        this.last = last;
    }

    public BigDecimal getDayClose() {
        return dayClose;
    }

    public void setDayClose(BigDecimal dayClose) {
        this.dayClose = dayClose;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    public BigDecimal getChangeAmount() {
        return changeAmount;
    }

    public void setChangeAmount(BigDecimal changeAmount) {
        this.changeAmount = changeAmount;
    }

    public BigDecimal getChangePercent() {
        return changePercent;
    }

    public void setChangePercent(BigDecimal changePercent) {
        this.changePercent = changePercent;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public BigDecimal getSettlement() {
        return settlement;
    }

    public void setSettlement(BigDecimal settlement) {
        this.settlement = settlement;
    }

    public BigDecimal getPreSettlement() {
        return preSettlement;
    }

    public void setPreSettlement(BigDecimal preSettlement) {
        this.preSettlement = preSettlement;
    }

    public BigDecimal getLimitUp() {
        return limitUp;
    }

    public void setLimitUp(BigDecimal limitUp) {
        this.limitUp = limitUp;
    }

    public BigDecimal getLimitDown() {
        return limitDown;
    }

    public void setLimitDown(BigDecimal limitDown) {
        this.limitDown = limitDown;
    }

    public BigDecimal getPriceStep() {
        return priceStep;
    }

    public void setPriceStep(BigDecimal priceStep) {
        this.priceStep = priceStep;
    }

    public BigDecimal getInitialMargin() {
        return initialMargin;
    }

    public void setInitialMargin(BigDecimal initialMargin) {
        this.initialMargin = initialMargin;
    }

    public BigDecimal getWeekLow() {
        return weekLow;
    }

    public void setWeekLow(BigDecimal weekLow) {
        this.weekLow = weekLow;
    }

    public BigDecimal getWeekHigh() {
        return weekHigh;
    }

    public void setWeekHigh(BigDecimal weekHigh) {
        this.weekHigh = weekHigh;
    }

    public BigDecimal getWeekClose() {
        return weekClose;
    }

    public void setWeekClose(BigDecimal weekClose) {
        this.weekClose = weekClose;
    }

    public BigDecimal getMonthLow() {
        return monthLow;
    }

    public void setMonthLow(BigDecimal monthLow) {
        this.monthLow = monthLow;
    }

    public BigDecimal getMonthHigh() {
        return monthHigh;
    }

    public void setMonthHigh(BigDecimal monthHigh) {
        this.monthHigh = monthHigh;
    }

    public BigDecimal getMonthClose() {
        return monthClose;
    }

    public void setMonthClose(BigDecimal monthClose) {
        this.monthClose = monthClose;
    }

    public BigDecimal getYearClose() {
        return yearClose;
    }

    public void setYearClose(BigDecimal yearClose) {
        this.yearClose = yearClose;
    }

    public BigDecimal getPrevYearClose() {
        return prevYearClose;
    }

    public void setPrevYearClose(BigDecimal prevYearClose) {
        this.prevYearClose = prevYearClose;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
