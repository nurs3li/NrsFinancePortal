package com.nurseli.marketdata.api.dto;

import java.time.LocalDate;

public record CryptoHistoryWarmupResponse(
        String status,
        int requestedSymbols,
        int queuedSymbols,
        LocalDate requestedFrom,
        LocalDate requestedTo,
        String message
) {}
