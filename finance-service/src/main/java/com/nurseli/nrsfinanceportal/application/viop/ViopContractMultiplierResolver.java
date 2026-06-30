package com.nurseli.nrsfinanceportal.application.viop;

import com.nurseli.nrsfinanceportal.domain.viop.ManualViopPosition;
import com.nurseli.nrsfinanceportal.domain.viop.ViopCategory;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * finance-service VİOP kontrat çarpanı (sözleşme büyüklüğü) çözümleyici.
 *
 * Veri sağlayıcı (İş Yatırım VİOP snapshot) çarpan/contractSize alanı sunmadığı için
 * çarpan kullanıcıdan alınmaz; sembol ve kategoriye göre Borsa İstanbul VİOP standart
 * sözleşme büyüklükleri esas alınarak merkezi olarak belirlenir.
 *
 * <ul>
 *     <li>Pay vadeli (EQUITY): 100 pay/sözleşme</li>
 *     <li>Endeks vadeli (INDEX): 10</li>
 *     <li>Döviz vadeli (FX): 1000</li>
 *     <li>Ons altın (XAUUSD): 1 (fiyat zaten ons başına)</li>
 *     <li>Gram/TL altın (XAUTRY / XAUTRYM): 1 (fiyat zaten gram başına)</li>
 * </ul>
 */
public final class ViopContractMultiplierResolver {

    private static final BigDecimal EQUITY = new BigDecimal("100");
    private static final BigDecimal INDEX = new BigDecimal("10");
    private static final BigDecimal FX = new BigDecimal("1000");

    private ViopContractMultiplierResolver() {
    }

    /**
     * {@code resolve} — ManualViopPosition'dan çarpanı çözer.
     */
    public static BigDecimal resolve(ManualViopPosition position) {
        if (position == null) {
            return BigDecimal.ONE;
        }
        return resolve(position.getSymbol(), position.getUnderlyingSymbol(), position.getViopCategory());
    }

    /**
     * {@code resolve} — Sembol, dayanak ve kategoriden çarpanı çözer.
     */
    public static BigDecimal resolve(String symbol, String underlyingSymbol, ViopCategory category) {
        String sym = norm(symbol);
        String und = norm(underlyingSymbol);
        String hay = und.isEmpty() ? sym : und;

        // Altın (XAUTRY / XAUTRYM / XAUUSD): fiyat zaten birim (gram/ons) başına → çarpan 1.
        if (hay.contains("XAU")) {
            return BigDecimal.ONE;
        }

        if (category != null) {
            switch (category) {
                case EQUITY:
                    return EQUITY;
                case INDEX:
                    return INDEX;
                case FX:
                    return FX;
                default:
                    break;
            }
        }

        // Kategori yoksa sembolden döviz çıkarımı.
        if (hay.contains("USDTRY") || hay.contains("EURTRY")) {
            return FX;
        }

        return BigDecimal.ONE;
    }

    private static String norm(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().toUpperCase(Locale.ROOT).replace(" ", "");
    }
}
