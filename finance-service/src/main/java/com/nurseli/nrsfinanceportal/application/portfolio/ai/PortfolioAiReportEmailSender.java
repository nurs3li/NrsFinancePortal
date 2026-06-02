package com.nurseli.nrsfinanceportal.application.portfolio.ai;

import com.nurseli.nrsfinanceportal.api.dto.portfolioai.PortfolioAiAnalysisResponse;
import com.nurseli.nrsfinanceportal.api.dto.portfolioai.PortfolioAiAssetCommentDto;
import com.nurseli.nrsfinanceportal.api.dto.portfolioai.PortfolioAiDecisionPerspectiveDto;
import com.nurseli.nrsfinanceportal.api.dto.portfolioai.PortfolioAiMacroAndNewsImpactDto;
import com.nurseli.nrsfinanceportal.api.dto.portfolioai.PortfolioAiPortfolioOverviewDto;
import com.nurseli.nrsfinanceportal.api.exception.ApiBusinessException;
import com.nurseli.nrsfinanceportal.api.response.ApiErrorCode;
import com.nurseli.nrsfinanceportal.config.NotificationClientProperties;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakAdminTokenProvider;
import com.nurseli.nrsfinanceportal.application.auth.RegistrationEmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * finance-service portfolio AI rapor e-posta gönderici — analiz raporunu HTML e-posta olarak notification servisine iletir.
 */
@Slf4j
@RequiredArgsConstructor
@Service

public class PortfolioAiReportEmailSender {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm", Locale.forLanguageTag("tr"));

    private final NotificationClientProperties notificationClientProperties;
    private final KeycloakAdminTokenProvider tokenProvider;
    private final WebClient keycloakAdminWebClient;

    /**
     * {@code sendReport} — Portfolio AI analiz yanıtını hedef e-postaya formatlanmış rapor olarak gönderir.
     */
    public void sendReport(String toEmail, PortfolioAiAnalysisResponse report) {
        if (!tokenProvider.isConfigured()) {
            throw new ApiBusinessException(
    HttpStatus.SERVICE_UNAVAILABLE,
                    ApiErrorCode.INTERNAL_SERVER_ERROR,
                    "E-posta servisi yapılandırılmamış."
            );
        }
        String to = toEmail == null ? "" : toEmail.trim();
        if (to.isEmpty()) {
            throw new ApiBusinessException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.BAD_REQUEST,
                    "Hesabınızda kayıtlı e-posta bulunamadı."
            );
        }
        String title = report.title() != null && !report.title().isBlank()
                ? report.title().trim()
                : "Portföy AI analizi";
        String subject = "NRS Finance — Portföy AI Analizi: " + title;
        String body = buildBody(report, title);
        postEmail(notificationClientProperties.getBaseUrl(), new RegistrationEmailSender.EmailSendPayload(to, subject, body));
        log.info("[PORTFOLIO_AI_EMAIL] report sent analysisId={} to={}", report.id(), maskEmail(to));
    }

    private String buildBody(PortfolioAiAnalysisResponse r, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("Merhaba,\n\n");
        sb.append("Portföy AI analiz raporunuz hazır.\n\n");
        sb.append("— ").append(title).append(" —\n");
        if (r.createdAt() != null) {
            sb.append("Oluşturulma: ")
                    .append(DATE_FMT.format(r.createdAt().atZone(TZ)))
                    .append("\n");
        }
        sb.append("Portföy puanı: ").append(r.portfolioScore()).append("/100\n");
        sb.append("Risk puanı: ").append(r.riskScore()).append("/100\n");
        if (r.confidence() != null) {
            sb.append("Güven: ").append(r.confidence()).append("\n");
        }
        if (r.concentrationRisk() != null) {
            sb.append("Yoğunlaşma riski: ").append(r.concentrationRisk()).append("\n");
        }
        sb.append("\n");

        appendOverview(sb, r.portfolioOverview());
        appendTextBlock(sb, "Özet", firstNonBlank(
                r.portfolioOverview() != null ? r.portfolioOverview().summary() : null,
                r.summary()));
        appendBulletList(sb, "Öne çıkan bulgular", r.findings());

        PortfolioAiDecisionPerspectiveDto decision = r.decisionPerspective();
        if (decision != null) {
            appendTextBlock(sb, "Senaryo yorumu", firstNonBlank(decision.comment(), r.scenarioComment()));
            if (decision.shortTermView() != null && !decision.shortTermView().isBlank()) {
                appendTextBlock(sb, "Kısa vade", decision.shortTermView());
            }
            if (decision.mediumTermView() != null && !decision.mediumTermView().isBlank()) {
                appendTextBlock(sb, "Orta vade", decision.mediumTermView());
            }
            appendBulletList(sb, "İzlenecek noktalar", decision.watchPoints());
        } else if (r.scenarioComment() != null && !r.scenarioComment().isBlank()) {
            appendTextBlock(sb, "Senaryo yorumu", r.scenarioComment());
        }

        PortfolioAiMacroAndNewsImpactDto macro = r.macroAndNewsImpact();
        if (macro != null) {
            appendTextBlock(sb, "Makro ve haber etkisi", macro.summary());
            appendBulletList(sb, "İlgili başlıklar", macro.relevantItems());
        }

        List<PortfolioAiAssetCommentDto> assets = r.assetComments();
        if (assets != null && !assets.isEmpty()) {
            sb.append("\nVarlık özeti (ilk ").append(Math.min(8, assets.size())).append(")\n");
            int limit = Math.min(8, assets.size());
            for (int i = 0; i < limit; i++) {
                PortfolioAiAssetCommentDto a = assets.get(i);
                sb.append("• ")
                        .append(a.symbol())
                        .append(" — ağırlık %")
                        .append(formatPct(a.weightPct()))
                        .append(", puan ")
                        .append(a.assetScore());
                if (a.shortComment() != null && !a.shortComment().isBlank()) {
                    sb.append("\n  ").append(a.shortComment().trim());
                }
                sb.append("\n");
            }
        }

        String disclaimer = firstNonBlank(r.finalNote(), r.disclaimer());
        if (disclaimer != null) {
            sb.append("\n—\n").append(disclaimer.trim()).append("\n");
        }
        sb.append("\nNRS Finance Portal\n");
        sb.append("Bu analiz bilgilendirme amaçlıdır; yatırım tavsiyesi değildir.\n");
        return sb.toString();
    }

    private static void appendOverview(StringBuilder sb, PortfolioAiPortfolioOverviewDto o) {
        if (o == null) {
            return;
        }
        if (o.currentSituation() != null && !o.currentSituation().isBlank()) {
            appendTextBlock(sb, "Genel durum", o.currentSituation());
        }
        if (o.mainPositive() != null && !o.mainPositive().isBlank()) {
            appendTextBlock(sb, "Ana olumlu taraf", o.mainPositive());
        }
        if (o.mainRisk() != null && !o.mainRisk().isBlank()) {
            appendTextBlock(sb, "Ana risk", o.mainRisk());
        }
    }

    private static void appendTextBlock(StringBuilder sb, String label, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        sb.append(label).append("\n").append(text.trim()).append("\n\n");
    }

    private static void appendBulletList(StringBuilder sb, String label, List<String> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        sb.append(label).append("\n");
        for (String item : items) {
            if (item == null || item.isBlank()) {
                continue;
            }
            sb.append("• ").append(item.trim()).append("\n");
        }
        sb.append("\n");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String formatPct(double pct) {
        return String.format(Locale.US, "%.1f", pct);
    }

    private void postEmail(String baseUrl, RegistrationEmailSender.EmailSendPayload payload) {
        try {
            keycloakAdminWebClient
                    .post()
                    .uri(baseUrl + "/api/notifications/internal/email/send")
                    .headers(h -> h.setBearerAuth(tokenProvider.getBearerToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .onStatus(s -> !s.is2xxSuccessful(), response ->
                            response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(msg -> Mono.error(new IllegalStateException(msg))))
                    .toBodilessEntity()
                    .block();
        } catch (Exception ex) {
            log.warn("[PORTFOLIO_AI_EMAIL] send failed analysis to={} reason={}", maskEmail(payload.to()), ex.getMessage());
            throw new ApiBusinessException(
                    HttpStatus.BAD_GATEWAY,
                    ApiErrorCode.INTERNAL_SERVER_ERROR,
                    "Analiz raporu e-postası gönderilemedi. Lütfen kısa süre sonra tekrar deneyin."
            );
        }
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        if (local.length() <= 2) {
            return "**" + email.substring(at);
        }
        return local.substring(0, 2) + "***" + email.substring(at);
    }
}
