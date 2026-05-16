package com.nurseli.nrsfinanceportal.dto;

import java.util.List;

public record MarketTerminalListPageResponse(
        List<MarketTerminalListItemDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {}
