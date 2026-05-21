package com.nurseli.marketdata.api.dto.eurobond;

import java.math.BigDecimal;

public record EurobondInstrumentHistoryPointDto(String date, BigDecimal cleanPrice, BigDecimal yieldPct) {}
