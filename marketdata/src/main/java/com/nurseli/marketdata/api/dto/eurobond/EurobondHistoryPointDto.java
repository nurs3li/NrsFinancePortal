package com.nurseli.marketdata.api.dto.eurobond;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EurobondHistoryPointDto(LocalDate date, BigDecimal value) {}
