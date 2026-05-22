package com.nurseli.marketdata.domain.bankfx;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "bank_fx_latest",
        uniqueConstraints = @UniqueConstraint(name = "uq_bank_fx_latest_source_code_ccy", columnNames = {"source", "bank_code", "currency"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankFxLatest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "bank_code", nullable = false, length = 16)
    private String bankCode;

    @Column(name = "bank_name", nullable = false, length = 120)
    private String bankName;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(name = "buy_price", precision = 18, scale = 6)
    private BigDecimal buyPrice;

    @Column(name = "sell_price", precision = 18, scale = 6)
    private BigDecimal sellPrice;

    @Column(name = "change_pct", precision = 8, scale = 4)
    private BigDecimal changePct;

    @Column(name = "quote_time_text", length = 32)
    private String quoteTimeText;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
