package com.nurseli.marketdata.api.dto.eurobond;

import java.util.List;

public record EurobondInstrumentListResponse(boolean available, List<EurobondInstrumentItemDto> instruments) {}
