package com.nurseli.nrsfinanceportal.api.dto;

import java.util.List;

/**
 * Piyasa terminali sayfalı liste response'u; öğeler ve sayfalama meta verisini taşır.
 */
public record MarketTerminalListPageResponse(
        List<MarketTerminalListItemDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {}
