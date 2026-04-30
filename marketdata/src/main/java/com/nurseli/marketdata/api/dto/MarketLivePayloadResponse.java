package com.nurseli.marketdata.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MarketLivePayloadResponse(
        LocalDateTime ts,
        List<MarketLiveTickResponse> ticks
) {}
