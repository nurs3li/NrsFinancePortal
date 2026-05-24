package com.nurseli.nrsfinanceportal.application.portfolio;

import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioInsightsResponse;
import com.nurseli.nrsfinanceportal.api.dto.PortfolioConcentrationRiskDto;
import com.nurseli.nrsfinanceportal.api.dto.PortfolioInsightsSummaryDto;
import com.nurseli.nrsfinanceportal.infrastructure.kafka.event.NotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * finance-service portfolio insight bildirim değerlendirici — insight metriklerinden Kafka NotificationRequestedEvent listesi üretir.
 */
@RequiredArgsConstructor
@Component

public class PortfolioInsightNotificationEvaluator {

    public static final String REFERENCE_TYPE = "PORTFOLIO_INSIGHT";

    public static final String TYPE_REAL_RETURN_NEGATIVE = "REAL_RETURN_NEGATIVE";
    public static final String TYPE_REAL_RETURN_POSITIVE = "REAL_RETURN_POSITIVE";
    public static final String TYPE_CONCENTRATION_RISK = "PORTFOLIO_CONCENTRATION_RISK";
    public static final String TYPE_PORTFOLIO_EVALUATION_REPORT = "PORTFOLIO_EVALUATION_REPORT";

    /**
     * {@code buildEvaluationReport} — Kullanıcı talebiyle gönderilecek portfolio değerlendirme raporu bildirim olayını oluşturur.
     */
    public NotificationRequestedEvent buildEvaluationReport(
            String targetKeycloakSub,
            Long referenceId,
            ManualPortfolioInsightsResponse insights) {

        if (targetKeycloakSub == null || targetKeycloakSub.isBlank() || insights == null) {
    return null;
        }

        StringBuilder body = new StringBuilder();
        body.append("Manuel portföyünüz için güncel değerlendirme özeti:\n\n");

        List<com.nurseli.nrsfinanceportal.api.dto.PortfolioInsightItemDto> items = insights.insights();
        if (items == null || items.isEmpty()) {
            body.append("Henüz değerlendirilecek pozisyon verisi bulunmuyor.\n");
        } else {
            int i = 1;
            for (var item : items) {
                if (item == null || item.message() == null || item.message().isBlank()) {
                    continue;
                }
                body.append(i++)
                        .append(". [")
                        .append(severityLabelTr(item.uiSeverity()))
                        .append("] ")
                        .append(item.message().trim())
                        .append("\n");
            }
        }

        body.append("\nBu özet bilgilendirme amaçlıdır; yatırım kararı öncesi kendi analizinizi de yapmanız önerilir.");

        return new NotificationRequestedEvent(
                targetKeycloakSub,
                "Portföy değerlendirmeniz",
                body.toString(),
                TYPE_PORTFOLIO_EVALUATION_REPORT,
                REFERENCE_TYPE,
                referenceId
        );
    }

    private static String severityLabelTr(String uiSeverity) {
        if (uiSeverity == null) {
            return "Bilgi";
        }
        return switch (uiSeverity.toUpperCase()) {
            case "CRITICAL", "RISK" -> "Risk";
            case "WARNING", "ATTENTION" -> "Dikkat";
            case "POSITIVE", "SUCCESS" -> "Olumlu";
            default -> "Bilgi";
        };
    }

    /**
     * {@code evaluate} — Reel getiri ve konsantrasyon koşullarına göre otomatik insight bildirim olaylarını üretir.
     */
    public List<NotificationRequestedEvent> evaluate(
            String targetKeycloakSub,
            Long referenceId,
    ManualPortfolioInsightsResponse insights) {

        List<NotificationRequestedEvent> events = new ArrayList<>();
        if (targetKeycloakSub == null || targetKeycloakSub.isBlank() || insights == null) {
            return events;
        }

        PortfolioInsightsSummaryDto s = insights.summary();
        if (s != null && s.realReturnAvailable() && s.realReturn() != null) {
            if (s.realReturn().compareTo(BigDecimal.ZERO) < 0) {
                events.add(new NotificationRequestedEvent(
                        targetKeycloakSub,
                        "Reel getiri negatife düştü",
                        "Portföyünüz nominal olarak kazançta olsa da TÜFE dikkate alındığında reel getiriniz negatiftir.",
                        TYPE_REAL_RETURN_NEGATIVE,
                        REFERENCE_TYPE,
                        referenceId
                ));
            } else if (s.realReturn().compareTo(BigDecimal.ZERO) > 0) {
                events.add(new NotificationRequestedEvent(
                        targetKeycloakSub,
                        "Reel getiri pozitif",
                        "Portföyünüz enflasyon etkisi dikkate alındığında da reel olarak pozitiftedir.",
                        TYPE_REAL_RETURN_POSITIVE,
                        REFERENCE_TYPE,
                        referenceId
                ));
            }
        }

        PortfolioConcentrationRiskDto c = insights.concentrationRisk();
        if (c != null && "HIGH".equals(c.riskLevel())) {
            String pct = c.topAssetWeightPct() != null
                    ? c.topAssetWeightPct().setScale(1, RoundingMode.HALF_UP).toPlainString()
                    : "?";
            String symbol = c.topAssetSymbol() != null ? c.topAssetSymbol() : "—";
            events.add(new NotificationRequestedEvent(
                    targetKeycloakSub,
                    "Konsantrasyon riski yüksek",
                    String.format(
                            "Portföyünüzün %%%s oranı %s varlığında yoğunlaşmış. Bu durum tek varlık riskini artırır.",
                            pct,
                            symbol),
                    TYPE_CONCENTRATION_RISK,
                    REFERENCE_TYPE,
                    referenceId
            ));
        }

        return events;
    }
}
