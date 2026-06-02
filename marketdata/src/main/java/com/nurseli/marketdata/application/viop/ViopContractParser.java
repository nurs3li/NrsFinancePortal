package com.nurseli.marketdata.application.viop;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ViopContractParser {
    private static final Pattern CODE_SUFFIX_MMYY = Pattern.compile("^(.*?)(\\d{2})(\\d{2})$");

    public String normalizeContractCode(String contractCode) {
        if (contractCode == null || contractCode.isBlank()) {
            return contractCode;
        }
        String normalized = contractCode.trim().toUpperCase();
        if (normalized.startsWith("F_")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    public String inferExpiryFromContractCode(String contractCode, String fallbackExpiry) {
        contractCode = normalizeContractCode(contractCode);
        if (fallbackExpiry != null && !fallbackExpiry.isBlank()) {
            return fallbackExpiry;
        }
        if (contractCode == null || contractCode.isBlank()) {
            return null;
        }
        Matcher m = CODE_SUFFIX_MMYY.matcher(contractCode.trim());
        if (!m.matches()) {
            return null;
        }
        int month = Integer.parseInt(m.group(2));
        int year = 2000 + Integer.parseInt(m.group(3));
        if (month < 1 || month > 12) {
            return null;
        }
        return YearMonth.of(year, month).atEndOfMonth().toString();
    }

    public Integer calculateDaysToExpiry(String expiry) {
        if (expiry == null || expiry.isBlank()) {
            return null;
        }
        try {
            LocalDate now = LocalDate.now();
            LocalDate exp = LocalDate.parse(expiry);
            return (int) ChronoUnit.DAYS.between(now, exp);
        } catch (Exception ignored) {
            return null;
        }
    }
}

