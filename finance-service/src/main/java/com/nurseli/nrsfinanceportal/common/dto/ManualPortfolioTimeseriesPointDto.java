package com.nurseli.nrsfinanceportal.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Günlük manuel portföy: açık pozisyonların maliyet tabanı ve (piyasa kapanışları mevcutsa) piyasa değeri.
 */
public record ManualPortfolioTimeseriesPointDto(
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate date,
        BigDecimal openCostBasisTry,
        BigDecimal marketValueTry
) {}
