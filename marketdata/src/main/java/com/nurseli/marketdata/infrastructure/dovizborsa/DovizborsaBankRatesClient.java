package com.nurseli.marketdata.infrastructure.dovizborsa;

import com.nurseli.marketdata.config.BankRatesProperties;
import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class DovizborsaBankRatesClient {

    private final BankRatesProperties properties;

    public String fetchHtml() throws IOException {
        int timeoutMs = Math.max(properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
        return Jsoup.connect(properties.getUrl())
                .userAgent(properties.getUserAgent())
                .timeout(timeoutMs)
                .ignoreContentType(true)
                .followRedirects(true)
                .get()
                .html();
    }
}
