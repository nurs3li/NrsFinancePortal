package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.fund.FundRequest;
import com.nurseli.nrsfinanceportal.domain.fund.FundRequestType;
import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.integration.kafka.NotificationEventKafkaPublisher;
import com.nurseli.nrsfinanceportal.integration.kafka.event.NotificationRequestedEvent;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class FundRequestNotificationHelper {

    private final NotificationEventKafkaPublisher publisher;
    private final UserRepository userRepository;

    public void notifyCreatedForFinanceManagers(FundRequest request) {
        try {
            boolean deposit = request.getType() == FundRequestType.DEPOSIT;
            String title = deposit
                    ? "Manuel inceleme — Para yatırma talebi #" + request.getId()
                    : "Manuel inceleme — Para çekme talebi #" + request.getId();

            String body = buildCreatedBodyForFm(request, deposit);

            userRepository.findByRole(Role.FINANCE_MANAGER).forEach(fm ->
                    publisher.publish(new NotificationRequestedEvent(
                            fm.getKeycloakUserId(),
                            title,
                            body,
                            "FUND_REQUEST_CREATED",
                            "fund_request",
                            request.getId()
                    ))
            );
        } catch (Exception ex) {
            log.error("[FUND_REQUEST][NOTIFY] created notify failed. requestId={}", request.getId(), ex);
        }
    }

    public void notifyApproved(FundRequest request) {
        try {
            String sub = request.getUser().getKeycloakUserId();
            String title = "Para talebiniz onaylandı (#%d)".formatted(request.getId());

            String body = """
                    Merhaba,

                    Para talebiniz onaylanmıştır.

                    • Talep No: %d
                    • İşlem türü: %s
                    • Tutar: %s %s
                    • FM notu: %s

                    İşlem hesap bakiyenize yansıtılmıştır. Portaldan bakiyenizi kontrol edebilirsiniz.

                    İyi günler dileriz,
                    NRS Finance Portal
                    """.formatted(
                    request.getId(),
                    prettyType(request.getType().name()),
                    formatAmount(request.getAmount()),
                    nvl(request.getCurrency(), "TRY"),
                    nvl(request.getReviewNote(), "-")
            );

            publisher.publish(new NotificationRequestedEvent(
                    sub,
                    title,
                    body,
                    "FUND_REQUEST_APPROVED",
                    "fund_request",
                    request.getId()
            ));
        } catch (Exception ex) {
            log.error("[FUND_REQUEST][NOTIFY] approved notify failed. requestId={}", request.getId(), ex);
        }
    }

    public void notifyRejected(FundRequest request) {
        try {
            String sub = request.getUser().getKeycloakUserId();
            String title = "Para talebiniz reddedildi (#%d)".formatted(request.getId());

            String body = """
                    Merhaba,

                    Para talebiniz bu aşamada onaylanmamıştır.

                    • Talep No: %d
                    • İşlem türü: %s
                    • Tutar: %s %s
                    • İnceleme notu: %s

                    Bilgilerinizi güncelleyerek uygun şartlarda yeni bir talep oluşturabilirsiniz.

                    Saygılarımızla,
                    NRS Finance Portal
                    """.formatted(
                    request.getId(),
                    prettyType(request.getType().name()),
                    formatAmount(request.getAmount()),
                    nvl(request.getCurrency(), "TRY"),
                    nvl(request.getReviewNote(), "-")
            );

            publisher.publish(new NotificationRequestedEvent(
                    sub,
                    title,
                    body,
                    "FUND_REQUEST_REJECTED",
                    "fund_request",
                    request.getId()
            ));
        } catch (Exception ex) {
            log.error("[FUND_REQUEST][NOTIFY] rejected notify failed. requestId={}", request.getId(), ex);
        }
    }

    private static String buildCreatedBodyForFm(FundRequest request, boolean deposit) {
        String intro = deposit
                ? """
                        Merhaba,

                        Sistemde manuel inceleme gerektiren bir para yatırma talebi oluşturulmuştur.
                        """
                : """
                        Merhaba,

                        Sistemde manuel inceleme gerektiren bir para çekme talebi oluşturulmuştur.
                        Not: Talep, bakiye kontrolünden geçmiştir.
                        """;

        StringBuilder sb = new StringBuilder(intro.trim()).append("\n\n");
        sb.append("Talep özeti:\n");
        sb.append("• Talep No: ").append(request.getId()).append("\n");
        sb.append("• Tür: ").append(prettyType(request.getType().name())).append("\n");
        sb.append("• Tutar: ").append(formatAmount(request.getAmount())).append(" ")
                .append(nvl(request.getCurrency(), "TRY")).append("\n");
        sb.append("• Kullanıcı: ").append(nvl(request.getUser().getUsername(), "—")).append("\n");
        sb.append("• E-posta: ").append(nvl(request.getUser().getEmail(), "—")).append("\n");

        if (deposit) {
            sb.append("• Yatırım / sistem IBAN: ").append(nvl(firstNonBlank(request.getDepositIban(), request.getBankAccountIban()), "—")).append("\n");
            sb.append("• Kaynak banka: ").append(nvl(request.getSourceBankName(), "—")).append("\n");
            sb.append("• Referans / dekont: ").append(nvl(request.getReferenceNo(), "—")).append(" / ")
                    .append(nvl(request.getReceiptFileUrl(), nvl(request.getReceiptFileId(), "—"))).append("\n");
        } else {
            sb.append("• Hedef IBAN: ").append(nvl(request.getDestinationIban(), "—")).append("\n");
            sb.append("• Alıcı adı: ").append(nvl(request.getDestinationAccountHolder(), "—")).append("\n");
            sb.append("• Banka: ").append(nvl(request.getDestinationBankName(), "—")).append("\n");
        }

        sb.append("• Kullanıcı notu: ").append(nvl(request.getRequestNote(), "—")).append("\n\n");
        sb.append("Bu talep finans yöneticileri havuzundadır; ilk aksiyonu alan görevi üzerine alır.\n");
        sb.append("İyi çalışmalar.\nNRS Finance Portal");
        return sb.toString();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private static String prettyType(String type) {
        if ("DEPOSIT".equalsIgnoreCase(type)) return "Para yatırma";
        if ("WITHDRAWAL".equalsIgnoreCase(type)) return "Para çekme";
        return type;
    }

    private static String nvl(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private static String formatAmount(BigDecimal amount) {
        if (amount == null) return "0";
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("tr", "TR"));
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('.');
        DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
        return df.format(amount);
    }
}