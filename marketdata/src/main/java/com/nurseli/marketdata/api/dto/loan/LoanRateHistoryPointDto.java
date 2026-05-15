package com.nurseli.marketdata.api.dto.loan;

import java.math.BigDecimal;

/** Haftalık kredi faizi (borçlanma maliyeti) tek gözlem — yatırım getirisi değildir. */
public record LoanRateHistoryPointDto(
        String date,
        BigDecimal value
) {}
