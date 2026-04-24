package com.nurseli.nrsfinanceportal.common.dto;

public record ReceiptUploadResponseDto(
        String receiptFileId,
        String receiptFileUrl,
        String originalFileName,
        long fileSize
) {
}
