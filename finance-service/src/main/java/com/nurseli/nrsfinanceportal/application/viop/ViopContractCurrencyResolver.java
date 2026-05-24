package com.nurseli.nrsfinanceportal.application.viop;

import com.nurseli.nrsfinanceportal.domain.viop.ManualViopPosition;
import com.nurseli.nrsfinanceportal.domain.viop.ViopCategory;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * finance-service VIOP kontrat para birimi çözümleyici — sembol ve kategoriden kotasyon para birimini (TRY/USD/EUR) belirler.
 */
public final class ViopContractCurrencyResolver {

    private static final Pattern SUFFIX = Pattern.compile("^F?_?([A-Z0-9]+?)(\\d{2})(\\d{2})$", Pattern.CASE_INSENSITIVE);
private ViopContractCurrencyResolver() {}

    /**
     * {@code resolve} — ManualViopPosition veya sembol/underlying/kategori bilgisinden ViopQuoteCurrency döner.
     */
    public static ViopQuoteCurrency resolve(ManualViopPosition position) {
        if (position == null) {
            return ViopQuoteCurrency.TRY;
    }
        return resolve(position.getSymbol(), position.getUnderlyingSymbol(), position.getViopCategory());
    }

    /**
     * {@code resolve} — ManualViopPosition veya sembol/underlying/kategori bilgisinden ViopQuoteCurrency döner.
     */
    public static ViopQuoteCurrency resolve(String symbol, String underlyingSymbol, ViopCategory category) {
        String sym = norm(symbol);
        String und = norm(underlyingSymbol);
    String hay = und.isEmpty() ? sym : und;

        if (hay.contains("XAUUSD") || hay.contains("XAGUSD") || hay.contains("XPTUSD") || hay.contains("XPDUSD")) {
            return ViopQuoteCurrency.USD;
        }
        if (hay.endsWith("USD") && !hay.contains("TRY") && !hay.endsWith("USDTRY")) {
            return ViopQuoteCurrency.USD;
        }
        if (hay.contains("EURUSD") && !hay.contains("TRY")) {
            return ViopQuoteCurrency.USD;
        }
        if (hay.endsWith("EUR") && !hay.contains("TRY") && category != ViopCategory.FX) {
            return ViopQuoteCurrency.EUR;
        }
        if (hay.endsWith("TRY") || hay.contains("TRY") || hay.contains("TL")) {
            return ViopQuoteCurrency.TRY;
        }
        Matcher m = SUFFIX.matcher(sym.replace("F_", ""));
        if (m.matches()) {
            return resolve(m.group(1), null, category);
        }
        return ViopQuoteCurrency.TRY;
    }

    private static String norm(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().toUpperCase(Locale.ROOT).replace(" ", "");
    }
}
