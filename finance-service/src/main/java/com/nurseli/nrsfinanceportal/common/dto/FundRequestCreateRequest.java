package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.fund.FundRequestType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class FundRequestCreateRequest {

    @NotNull
    private FundRequestType type;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    private String currency;
    private String requestNote;
    private String bankAccountIban; // legacy alias (mapped to deposit/destination iban)
    private Long accountId;

    private String receiptFileUrl;
    private String receiptFileId;
    private String referenceNo;
    private String sourceBankName;
    private String externalReferenceNo;

    private String depositIban;
    private String systemIbanId;

    private String destinationIban;
    private String destinationAccountHolder;
    private String destinationBankName;

    public FundRequestType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getRequestNote() { return requestNote; }
    public String getBankAccountIban() { return bankAccountIban; }
    public Long getAccountId() { return accountId; }
    public String getReceiptFileUrl() { return receiptFileUrl; }
    public String getReceiptFileId() { return receiptFileId; }
    public String getReferenceNo() { return referenceNo; }
    public String getSourceBankName() { return sourceBankName; }
    public String getExternalReferenceNo() { return externalReferenceNo; }
    public String getDepositIban() { return depositIban; }
    public String getSystemIbanId() { return systemIbanId; }
    public String getDestinationIban() { return destinationIban; }
    public String getDestinationAccountHolder() { return destinationAccountHolder; }
    public String getDestinationBankName() { return destinationBankName; }
}