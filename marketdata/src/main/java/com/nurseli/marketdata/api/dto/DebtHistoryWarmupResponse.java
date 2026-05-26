package com.nurseli.marketdata.api.dto;

import java.time.LocalDate;

public record DebtHistoryWarmupResponse(
        String status,
        int requestedIsins,
        int queuedIsins,
        LocalDate requestedFrom,
        LocalDate requestedTo,
        String message
) {}
