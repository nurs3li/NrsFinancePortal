
package com.nurseli.marketdata.infrastructure.tefas;
import java.util.concurrent.ThreadLocalRandom; // Bunu importlara ekle
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import java.util.Optional;


@Component
@RequiredArgsConstructor
@Slf4j
public class TefasClient {

    private final WebClient tefasWebClient;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");


    public Optional<TefasFundPriceDto> fetchLastAvailablePrice(String fundCode, LocalDate referenceDate) {
        try {
            // Canlı veri çekme denemesi (Şimdilik DNS hatası nedeniyle doğrudan catch'e düşüyor)
            throw new RuntimeException("DNS_ISSUE");
        } catch (Exception e) {
            log.warn("[FUND] {} için canlı veri yok, random fiyat üretiliyor.", fundCode);

            // Senin listendeki tüm fonlar ve gerçekçi taban fiyatları
            double basePrice = switch (fundCode) {
                case "AES"   -> 0.452310; // Hisse
                case "AFT"   -> 0.128540; // Hisse
                case "TCD"   -> 5.423100; // Hisse
                case "GAF"   -> 0.084210; // Borçlanma
                case "DBH"   -> 1.254300; // Borçlanma
                case "GLDTR" -> 0.996224; // Altın
                case "KTN"   -> 0.412500; // Katılım
                case "KZL"   -> 0.385400; // Katılım
                case "YAC"   -> 0.995252; // Değişken
                default      -> 1.000000;
            };

            // 2. Fiyata % -0.5 ile % +0.5 arasında küçük bir rastgelelik ekleyelim
            // Bu sayede grafiklerde küçük iniş çıkışlar görünür
            double randomVariation = 0.995 + (1.005 - 0.995) * ThreadLocalRandom.current().nextDouble();
            BigDecimal finalPrice = BigDecimal.valueOf(basePrice * randomVariation)
                    .setScale(6, RoundingMode.HALF_UP);

            return Optional.of(new TefasFundPriceDto(fundCode, finalPrice, referenceDate));
        }
    }
}