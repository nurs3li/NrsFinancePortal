package com.nurseli.nrsfinanceportal.common.dto;

import java.util.List;

public record PortfolioHealthScoreDto(
        int score,
        String level,
        String summary,
        List<String> factors
) {}
