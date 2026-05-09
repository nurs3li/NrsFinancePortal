package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.crypto.history-backfill")
public class CryptoHistoryBackfillProperties {
    private boolean enabled = false;
    private int periodDays = 365;
    private boolean shutdownOnComplete = false;
    /**
     * CoinGecko ücretsiz kotada OHLC başına bekleme (ms). Çok düşükse 429 alırsınız.
     */
    private int delayMsBetweenCoins = 4500;
    /**
     * Son N güne ait mum sayısı bu eşiğe ulaştıysa sembol için OHLC isteği atlanır (yeniden başlatmada gereksiz çağrı önleme).
     */
    private int skipSymbolIfCandleCountAtLeast = 340;
}
