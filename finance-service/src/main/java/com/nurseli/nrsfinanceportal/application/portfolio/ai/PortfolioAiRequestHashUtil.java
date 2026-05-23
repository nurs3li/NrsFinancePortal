package com.nurseli.nrsfinanceportal.application.portfolio.ai;

import com.nurseli.nrsfinanceportal.api.dto.portfolioai.PortfolioAiAnalysisRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;

/**
 * finance-service portfolio AI istek hash yardımcısı — tekrarlayan analiz isteklerini tespit için hash üretir.
 */
public final class PortfolioAiRequestHashUtil {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");
private PortfolioAiRequestHashUtil() {
    }

    /**
     * {@code hash} — Kullanıcı ID, istek ve context snapshot'tan SHA-256 tabanlı istek hash'i üretir.
     */
    public static String hash(Long userId, PortfolioAiAnalysisRequest request, PortfolioAiContextSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append(userId).append('|');
    sb.append(request.title().trim()).append('|');
        sb.append(request.analysisType()).append('|');
        sb.append(request.riskProfile()).append('|');
        sb.append(request.detailLevel()).append('|');
        sb.append(request.includeNews()).append('|');
        sb.append(request.includeMacro()).append('|');
        sb.append(request.includeRealReturn()).append('|');
        sb.append(LocalDate.now(TZ)).append('|');
        for (PortfolioAiContextSnapshot.PositionLine line : snapshot.topByWeight()) {
            sb.append(line.symbol())
                    .append(':')
                    .append(String.format("%.2f", line.weightPct()))
                    .append(':')
                    .append(line.currentValue())
                    .append(';');
        }
        return sha256Hex(sb.toString());
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
