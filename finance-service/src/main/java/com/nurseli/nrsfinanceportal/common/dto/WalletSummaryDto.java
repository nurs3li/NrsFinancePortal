package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;

public record WalletSummaryDto(
        Long accountId,
        BigDecimal currentBalance,
        BigDecimal availableBalance,
        BigDecimal pendingDeposit,
        BigDecimal pendingWithdrawal
) {
}
