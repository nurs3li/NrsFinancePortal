package com.nurseli.marketdata.infrastructure.dovizborsa;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class DovizborsaBankRatesParser {

    private static final Set<String> SUPPORTED_CCY = Set.of("USD", "EUR", "GBP");

    public List<DovizborsaParsedRate> parse(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        Document doc = Jsoup.parse(html);
        List<DovizborsaParsedRate> out = new ArrayList<>();
        for (Element row : doc.select("div.-r")) {
            Element codeEl = row.selectFirst("span.-g0");
            if (codeEl == null) {
                continue;
            }
            String bankCode = codeEl.text().trim().toUpperCase(Locale.ROOT);
            if (bankCode.length() < 6) {
                continue;
            }
            String currency = bankCode.substring(bankCode.length() - 3);
            if (!SUPPORTED_CCY.contains(currency)) {
                continue;
            }
            String label = textOf(row.selectFirst("span.-g1"));
            BigDecimal buy = parsePriceSpan(row.selectFirst("span.-g2"));
            BigDecimal sell = parsePriceSpan(row.selectFirst("span.-g3"));
            if (buy == null || sell == null) {
                continue;
            }
            BigDecimal changePct = parseChange(row.selectFirst("span.-g4"));
            String quoteTime = textOf(row.selectFirst("span.-g5"));
            String trend = parseTrend(row.selectFirst("span.-g6"));
            String bankName = extractBankName(label);
            out.add(new DovizborsaParsedRate(bankCode, bankName, currency, buy, sell, changePct, quoteTime, trend));
        }
        return out;
    }

    static String extractBankName(String label) {
        if (label == null || label.isBlank()) {
            return "";
        }
        int slash = label.indexOf('/');
        if (slash > 0) {
            return label.substring(0, slash).trim();
        }
        return label.trim();
    }

    static BigDecimal parsePriceSpan(Element span) {
        if (span == null) {
            return null;
        }
        StringBuilder main = new StringBuilder();
        for (Node node : span.childNodes()) {
            if (node instanceof TextNode tn) {
                main.append(tn.getWholeText());
            }
        }
        String mainText = main.toString().trim();
        if (mainText.isEmpty()) {
            mainText = span.ownText().trim();
        }
        Element sup = span.selectFirst("sup");
        String supDigits = sup != null ? sup.text().trim() : "";
        if (mainText.isEmpty()) {
            return null;
        }
        String normalized = mainText.replace(',', '.');
        try {
            if (!supDigits.isEmpty() && normalized.contains(".")) {
                int dot = normalized.indexOf('.');
                String combined = normalized.substring(0, dot) + "." + normalized.substring(dot + 1) + supDigits;
                return new BigDecimal(combined);
            }
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal parseChange(Element el) {
        if (el == null) {
            return null;
        }
        String raw = el.text().trim().replace(',', '.');
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String parseTrend(Element trendCell) {
        if (trendCell == null) {
            return "FLAT";
        }
        Element icon = trendCell.selectFirst("i");
        if (icon == null) {
            return "FLAT";
        }
        String cls = icon.className();
        if (cls.contains("__u")) {
            return "UP";
        }
        if (cls.contains("__d")) {
            return "DOWN";
        }
        return "FLAT";
    }

    private static String textOf(Element el) {
        return el == null ? "" : el.text().trim();
    }
}
