package com.nurseli.nrsfinanceportal.common.dto;

public record WalletDepositInstructionsDto(
        String iban,
        String recipientName,
        String bankName,
        String userReferenceCode,
        String systemIbanId
) {
}
