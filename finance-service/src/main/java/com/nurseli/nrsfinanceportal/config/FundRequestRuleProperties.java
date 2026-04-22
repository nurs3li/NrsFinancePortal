package com.nurseli.nrsfinanceportal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Data
@Component
@ConfigurationProperties(prefix = "app.fund-request")
public class FundRequestRuleProperties {

    private boolean autoApprovalEnabled = true;

    /**
     * TRY cinsinden küçük DEPOSIT talepleri otomatik onay limiti
     */
    private BigDecimal depositAutoApproveLimitTry = new BigDecimal("10000");

    /**
     * TRY cinsinden küçük WITHDRAWAL talepleri otomatik onay limiti
     */
    private BigDecimal withdrawalAutoApproveLimitTry = new BigDecimal("3000");

    /**
     * DEPOSIT auto-approve için zorunlu alan kontrolleri
     */
    private boolean requireReceiptForDeposit = true;
    private boolean requireReferenceNoForDeposit = true;
    private boolean requireIbanForDeposit = true;
}