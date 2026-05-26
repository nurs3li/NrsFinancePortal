package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "market.bootstrap")
@Data
public class MarketBootstrapProperties {

    /** Tek orchestrator; false ise legacy ayrı startup listener'lar çalışır. */
    private boolean orchestrateStartup = true;

    /** İlk fazdan önce bekleme (TCMB/VIOP startup yükü). */
    private long initialDelayMs = 45_000;

    /** EVDS fazları arası (enflasyon → mevduat → eurobond). */
    private long phaseDelayMs = 8_000;

    /** EVDS HTTP istekleri arası minimum süre (rate limit). */
    private long evdsMinIntervalMs = 1_200;

    /** İş Yatırım maden sembolleri arası bekleme. */
    private long metalsDelayMs = 2_500;

    private boolean metalsStartupEnabled = true;

    /** Eurobond: satır sayısı bu eşiğin altındaysa tam seed (from config). */
    private int eurobondFullSeedRowThreshold = 20;

    /** Kredi faizleri: kısmi/tekil seed tespitinde tam geçmiş ingest tetikler. */
    private int loanRatesFullSeedRowThreshold = 20;

    /** Eurobond dolu DB'de son N hafta refresh (gap yoksa bile makro güncelleme). */
    private int eurobondRecentWeeksWhenSeeded = 4;
}
