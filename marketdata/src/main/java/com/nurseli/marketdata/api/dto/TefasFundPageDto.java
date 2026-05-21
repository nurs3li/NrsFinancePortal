package com.nurseli.marketdata.api.dto;

import java.util.List;

public record TefasFundPageDto(
        List<TefasFundRowDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {}
