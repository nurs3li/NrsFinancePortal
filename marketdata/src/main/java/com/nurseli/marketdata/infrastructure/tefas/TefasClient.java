
package com.nurseli.marketdata.infrastructure.tefas;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class TefasClient {

    private final WebClient tefasWebClient;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public Optional<TefasFundPriceDto> fetchLastAvailablePrice(String fundCode, LocalDate referenceDate) {
        try {
            // 🔥 TEFAS YERİNE DAHA STABİL BİR TOPLULUK API'SI KULLANIYORUZ
            String url = "https://api.finfree.co/v1/investment-funds/" + fundCode;

            // WebClient ile JSON verisini çekiyoruz
            Map<String, Object> response = tefasWebClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(java.time.Duration.ofSeconds(10));

            if (response != null && response.containsKey("price")) {
                // Gelen veriyi güvenli bir şekilde BigDecimal'e çeviriyoruz
                BigDecimal price = new BigDecimal(response.get("price").toString());
                log.info("[FUND] {} fiyatı başarıyla çekildi: {}", fundCode, price);

                return Optional.of(new TefasFundPriceDto(fundCode, price, referenceDate));
            }
        } catch (Exception e) {
            log.error("[FUND] {} çekilirken hata oluştu: {}", fundCode, e.getMessage());
        }
        return Optional.empty();
    }
}