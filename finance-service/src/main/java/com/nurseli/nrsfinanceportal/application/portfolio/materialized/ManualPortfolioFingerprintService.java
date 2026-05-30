package com.nurseli.nrsfinanceportal.application.portfolio.materialized;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualPortfolioPositionRepository;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ManualPortfolioFingerprintService {

    private final ManualPortfolioPositionRepository positionRepository;

    public String computeForUser(Long userId) {
        return compute(positionRepository.findByUserIdOrderByBuyDateAsc(userId));
    }

    public String compute(List<ManualPortfolioPosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return hashString("empty");
        }
        String payload = positions.stream()
                .sorted(Comparator.comparing(ManualPortfolioPosition::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(p -> p.getId() + ":" + (p.getUpdatedAt() != null ? p.getUpdatedAt().toEpochMilli() : 0))
                .reduce((a, b) -> a + "|" + b)
                .orElse("empty");
        return hashString(payload);
    }

    private static String hashString(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
