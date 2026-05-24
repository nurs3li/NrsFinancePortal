package com.nurseli.nrsfinanceportal.application.portfolio.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * finance-service portfolio AI tavsiye temizleyici — yasaklı yatırım tavsiyesi ifadelerini metinden filtreler.
 */
@Component

public class PortfolioAiAdviceSanitizer {

    public static final String MECHANICAL_PHRASE = "karar destek çerçevesinde değerlendirildi";

    private static final Map<Pattern, String> REPLACEMENTS = linkedReplacements();

    private static final Pattern[] STILL_BANNED = {
            Pattern.compile("(?i)\\bal[iı\u0131]?nmal[iı\u0131]?\\b"),
            Pattern.compile("(?i)\\bsat[iı\u0131]?lmal[iı\u0131]?\\b"),
            Pattern.compile("(?i)\\bsatilmali\\b"),
            Pattern.compile("(?i)\\balinmali\\b"),
            Pattern.compile("(?i)\\bmutlaka\\s+al\\b"),
            Pattern.compile("(?i)\\bmutlaka\\s+sat\\b"),
            Pattern.compile("(?i)\\bkesin\\s+yükselir\\b"),
            Pattern.compile("(?i)\\bkesin\\s+düşer\\b"),
            Pattern.compile("(?i)\\bgaranti\\s+kazanç\\b"),
            Pattern.compile("(?i)\\bşimdi\\s+al\\b"),
            Pattern.compile("(?i)\\bhemen\\s+sat\\b"),
            Pattern.compile("(?i)\\bhemen\\s+al\\b"),
    };

    private static Map<Pattern, String> linkedReplacements() {
        Map<Pattern, String> map = new LinkedHashMap<>();
        map.put(Pattern.compile("(?i)\\bal[iı\u0131]?nmal[iı\u0131]?\\b"), "ekleme senaryosunda değerlendirilebilir");
        map.put(Pattern.compile("(?i)\\bsat[iı\u0131]?lmal[iı\u0131]?\\b"), "satış senaryosunda dikkatle değerlendirilmeli");
        map.put(Pattern.compile("(?i)\\bsatilmali\\b"), "satış senaryosunda dikkatle değerlendirilmeli");
        map.put(Pattern.compile("(?i)\\balinmali\\b"), "ekleme senaryosunda değerlendirilebilir");
        map.put(Pattern.compile("(?i)\\bmutlaka\\s+al\\b"), "ekleme senaryosunda dikkatle değerlendirilmeli");
        map.put(Pattern.compile("(?i)\\bmutlaka\\s+sat\\b"), "satış senaryosunda dikkatle değerlendirilmeli");
        map.put(Pattern.compile("(?i)\\bkesin\\s+yükselir\\b"), "yukarı yönlü potansiyel taşıyabilir");
        map.put(Pattern.compile("(?i)\\bkesin\\s+düşer\\b"), "aşağı yönlü risk taşıyabilir");
        map.put(Pattern.compile("(?i)\\bgaranti\\s+kazanç\\b"), "getiri garantisi verilemez");
        map.put(Pattern.compile("(?i)\\bşimdi\\s+al\\b"), "ekleme senaryosunda değerlendirilebilir");
        map.put(Pattern.compile("(?i)\\bhemen\\s+sat\\b"), "satış senaryosunda dikkatle değerlendirilmeli");
        map.put(Pattern.compile("(?i)\\bhemen\\s+al\\b"), "ekleme senaryosunda değerlendirilebilir");
        return map;
    }

    /**
     * {@code sanitizeText} — Metindeki yasaklı tavsiye kalıplarını nötr ifadelerle değiştirir.
     */
    public String sanitizeText(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
    }
        String out = text;
        for (Map.Entry<Pattern, String> e : REPLACEMENTS.entrySet()) {
            out = e.getKey().matcher(out).replaceAll(Matcher.quoteReplacement(e.getValue()));
        }
        return out;
    }

    /**
     * {@code sanitizeList} — String listesindeki her öğeyi sanitizeText ile temizler.
     */
    public List<String> sanitizeList(List<String> items) {
        if (items == null) {
            return List.of();
    }
        List<String> out = new ArrayList<>();
        for (String item : items) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String s = sanitizeText(item);
            if (!s.isBlank() && !containsMechanicalPhrase(s)) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * {@code containsStillBanned} — Metinde hâlâ yasaklı ifade kalıp kalmadığını kontrol eder.
     */
    public boolean containsStillBanned(String text) {
        if (text == null || text.isBlank()) {
            return false;
    }
        for (Pattern p : STILL_BANNED) {
            if (p.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code containsStillBannedInTexts} — Birden fazla metinde yasaklı ifade olup olmadığını kontrol eder.
     */
    public boolean containsStillBannedInTexts(String... texts) {
        for (String t : texts) {
            if (containsStillBanned(t)) {
    return true;
            }
        }
        return false;
    }

    /**
     * {@code containsMechanicalPhrase} — Mekanik karar destek ifadesinin metinde geçip geçmediğini kontrol eder.
     */
    public boolean containsMechanicalPhrase(String text) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(MECHANICAL_PHRASE);
    }
    }
