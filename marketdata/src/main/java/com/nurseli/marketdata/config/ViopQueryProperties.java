package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * VIOP sorgu (read) tarafı için whitelist konfigürasyonu.
 * <p>
 * {@link #allowedContracts} dolu ise {@code ViopQueryService} sadece bu kodları (normalize edilmiş)
 * publish eder. Liste boş ise davranış değişmez — tüm sözleşmeler dönülür.
 * <p>
 * Amaç: yalnız yeterli geçmiş veri noktası bulunan ve son 17 günde aktif olarak işlem gören
 * sözleşmeleri kullanıcıya göstermek; geçmişi olmayan/likiditesi düşük kontratlarda "veri yok"
 * mesajlarını engellemek.
 */
@Configuration
@ConfigurationProperties(prefix = "app.viop.query")
@Data
public class ViopQueryProperties {
    private List<String> allowedContracts = List.of();
}
