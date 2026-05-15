package com.nurseli.marketdata.domain.price;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "market_price_history")
@Getter
@Setter
@NoArgsConstructor
public class MarketPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(nullable = false, precision = 19, scale = 6, name = "buy_price")
    private BigDecimal buyPrice;

    @Column(nullable = false, precision = 19, scale = 6, name = "sell_price")
    private BigDecimal sellPrice;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "adjusted_close", precision = 24, scale = 8)
    private BigDecimal adjustedClose;

    @Column(name = "adjusted_average", precision = 24, scale = 8)
    private BigDecimal adjustedAverage;

    @Column(name = "adjusted_low", precision = 24, scale = 8)
    private BigDecimal adjustedLow;

    @Column(name = "adjusted_high", precision = 24, scale = 8)
    private BigDecimal adjustedHigh;

    @Column(name = "adjusted_volume", precision = 24, scale = 8)
    private BigDecimal adjustedVolume;

    @Column(name = "raw_close", precision = 24, scale = 8)
    private BigDecimal rawClose;

    @Column(name = "raw_average", precision = 24, scale = 8)
    private BigDecimal rawAverage;

    @Column(name = "raw_low", precision = 24, scale = 8)
    private BigDecimal rawLow;

    @Column(name = "raw_high", precision = 24, scale = 8)
    private BigDecimal rawHigh;

    @Column(name = "raw_volume", precision = 24, scale = 8)
    private BigDecimal rawVolume;

    @Column(name = "usd_try", precision = 24, scale = 8)
    private BigDecimal usdTry;

    @Column(name = "bist100_value", precision = 24, scale = 8)
    private BigDecimal bist100Value;

    @Column(name = "usd_price", precision = 24, scale = 8)
    private BigDecimal usdPrice;

    @Column(name = "index_based_price", precision = 24, scale = 8)
    private BigDecimal indexBasedPrice;

    @Column(name = "usd_volume", precision = 24, scale = 8)
    private BigDecimal usdVolume;

    @Column(precision = 24, scale = 2)
    private BigDecimal capital;

    @Column(name = "market_cap_try", precision = 24, scale = 2)
    private BigDecimal marketCapTry;

    @Column(name = "market_cap_usd", precision = 24, scale = 2)
    private BigDecimal marketCapUsd;

    @Column(name = "free_float_market_cap_try", precision = 24, scale = 2)
    private BigDecimal freeFloatMarketCapTry;

    @Column(name = "free_float_market_cap_usd", precision = 24, scale = 2)
    private BigDecimal freeFloatMarketCapUsd;

    @Column(name = "dollar_based_low", precision = 24, scale = 8)
    private BigDecimal dollarBasedLow;

    @Column(name = "dollar_based_high", precision = 24, scale = 8)
    private BigDecimal dollarBasedHigh;

    @Column(name = "dollar_based_average", precision = 24, scale = 8)
    private BigDecimal dollarBasedAverage;

    @Column(name = "data_quality", length = 32)
    private String dataQuality;

    @Column(length = 8)
    private String currency;
}
