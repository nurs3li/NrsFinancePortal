package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.equity")
public class EquityProperties {
    /** FinHub hisse sembolleri (örn. GARAN.IS, AKBNK.IS) */
    private List<String> symbols = new ArrayList<>();

    /**
     * Incremental dış kaynak çağrısında {@code lastAsOf+1}'den bu kadar gün geriye de bakılır (ara boşluk onarımı).
     * Örn. son mum 10 Mayıs iken 1–9 Mayıs boşsa, sadece 11 Mayıs'tan çekmek yetmez; Yahoo/Stooq penceresi geriye genişletilir.
     */
    private int incrementalGapHealDays = 21;
}