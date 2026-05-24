package com.nurseli.nrsfinanceportal.api.dto;

import java.util.List;

/**
 * Portföy sağlık skoru DTO'su; sayısal skor, seviye, özet ve etkileyen faktörleri taşır.
 */
public record PortfolioHealthScoreDto(
        int score,
        String level,
        String summary,
        List<String> factors
) {}
