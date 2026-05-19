package com.nurseli.nrsfinanceportal.service.pricealert;

import com.nurseli.nrsfinanceportal.domain.pricealert.PriceAlert;
import com.nurseli.nrsfinanceportal.domain.pricealert.PriceAlertChannels;
import com.nurseli.nrsfinanceportal.domain.pricealert.PriceAlertConditionType;
import com.nurseli.nrsfinanceportal.integration.kafka.NotificationEventKafkaPublisher;
import com.nurseli.nrsfinanceportal.integration.kafka.event.NotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class PriceAlertNotificationPublisher {

    public static final String TYPE_PRICE_ALERT_TRIGGERED = "PRICE_ALERT_TRIGGERED";
    public static final String TYPE_PRICE_ALERT_IN_APP = "PRICE_ALERT_IN_APP";
    public static final String REFERENCE_TYPE = "PRICE_ALERT";

    private final NotificationEventKafkaPublisher kafkaPublisher;

    public void publish(PriceAlert alert, PriceAlertMarketSnapshot snapshot) {
        if (alert == null || alert.getUser() == null) {
            return;
        }
        String sub = alert.getUser().getKeycloakUserId();
        if (sub == null || sub.isBlank()) {
            return;
        }

        String symbolLabel = displaySymbol(alert.getSymbol());
        String title = symbolLabel + " hedef fiyatınıza ulaştı";
        String body = buildBody(alert, snapshot, symbolLabel);

        String type = alert.getChannels() == PriceAlertChannels.IN_APP
                ? TYPE_PRICE_ALERT_IN_APP
                : TYPE_PRICE_ALERT_TRIGGERED;

        NotificationRequestedEvent event = new NotificationRequestedEvent(
                sub,
                title,
                body,
                type,
                REFERENCE_TYPE,
                alert.getId()
        );
        kafkaPublisher.publish(event);
    }

    private static String buildBody(PriceAlert alert, PriceAlertMarketSnapshot snapshot, String symbolLabel) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("tr", "TR"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);

        String thresholdStr = alert.getThreshold() != null
                ? nf.format(alert.getThreshold())
                : "—";

        return switch (alert.getConditionType()) {
            case PRICE_GTE -> symbolLabel + " hedef fiyatınıza ulaştı: "
                    + formatPrice(snapshot.priceTry(), nf) + " TL. (Eşik: ≥ " + thresholdStr + " TL)";
            case PRICE_LTE -> symbolLabel + " hedef fiyatınıza ulaştı: "
                    + formatPrice(snapshot.priceTry(), nf) + " TL. (Eşik: ≤ " + thresholdStr + " TL)";
            case CHANGE_PCT_GTE -> symbolLabel + " günlük değişim eşiğinizi aştı: "
                    + formatPct(snapshot.changePct(), nf) + "%. (Eşik: ≥ " + thresholdStr + "%)";
            case CHANGE_PCT_LTE -> symbolLabel + " değişim eşiğinize ulaştı: "
                    + formatPct(snapshot.changePct(), nf) + "%. (Eşik: ≤ " + thresholdStr + "%)";
        };
    }

    private static String formatPrice(BigDecimal price, NumberFormat nf) {
        if (price == null) {
            return "—";
        }
        return nf.format(price);
    }

    private static String formatPct(BigDecimal pct, NumberFormat nf) {
        if (pct == null) {
            return "—";
        }
        return nf.format(pct);
    }

    private static String displaySymbol(String symbol) {
        if (symbol == null) {
            return "";
        }
        String s = symbol.trim().toUpperCase();
        if ("XAU_TRY".equals(s)) {
            return "Gram altın";
        }
        if (s.endsWith("TRY") && s.length() > 3) {
            return s.substring(0, s.length() - 3) + "/TRY";
        }
        if (s.endsWith("USDT")) {
            return s.substring(0, s.length() - 4);
        }
        return s;
    }
}
