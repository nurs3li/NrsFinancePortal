package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.fund.FundRequest;
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
            String title = "Yeni para talebi #" + request.getId();

            String body = """
                    Yeni para talebi oluşturuldu.

                    Talep ID: %d
                    Tip: %s
                    Tutar: %s %s
                    Kullanıcı: %s
                    E-posta: %s
                    IBAN: %s
                    Kaynak banka: %s
                    Referans no: %s
                    Dekont: %s
                    Not: %s

                    Panelden onay/red işlemi yapabilirsiniz.
                    """.formatted(
                    request.getId(),
                    prettyType(request.getType().name()),
                    formatAmount(request.getAmount()),
                    nvl(request.getCurrency(), "TRY"),
                    nvl(request.getUser().getUsername(), "-"),
                    nvl(request.getUser().getEmail(), "-"),
                    nvl(request.getBankAccountIban(), "-"),
                    nvl(request.getSourceBankName(), "-"),
                    nvl(request.getReferenceNo(), "-"),
                    nvl(request.getReceiptFileUrl(), "-"),
                    nvl(request.getRequestNote(), "-")
            );

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
            String title = "Talebiniz onaylandı #" + request.getId();

            String body = """
                    Para talebiniz onaylandı.

                    Talep ID: %d
                    Tip: %s
                    Tutar: %s %s
                    İnceleme notu: %s

                    İşlem hesabınıza yansıtıldı.
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
            String title = "Talebiniz reddedildi #" + request.getId();

            String body = """
                    Para talebiniz reddedildi.

                    Talep ID: %d
                    Tip: %s
                    Tutar: %s %s
                    İnceleme notu: %s

                    Gerekirse yeni bir talep oluşturabilirsiniz.
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

    private static String prettyType(String type) {
        if ("DEPOSIT".equalsIgnoreCase(type)) return "Para Yatırma (DEPOSIT)";
        if ("WITHDRAWAL".equalsIgnoreCase(type)) return "Para Çekme (WITHDRAWAL)";
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