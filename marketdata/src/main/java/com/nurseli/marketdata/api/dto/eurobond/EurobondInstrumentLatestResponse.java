package com.nurseli.marketdata.api.dto.eurobond;

import java.util.List;

public record EurobondInstrumentLatestResponse(boolean available, List<EurobondInstrumentLatestRowDto> rows) {}
