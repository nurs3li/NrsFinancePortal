package com.nurseli.nrsfinanceportal.api.dto;

/**
 * Cüzdan para yatırma talimatları DTO'su; IBAN, alıcı ve kullanıcı referans kodunu taşır.
 */
public record WalletDepositInstructionsDto(
        String iban,
        String recipientName,
        String bankName,
        String userReferenceCode,
        String systemIbanId
) {
}
