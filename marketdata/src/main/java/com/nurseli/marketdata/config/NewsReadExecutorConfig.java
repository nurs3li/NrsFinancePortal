package com.nurseli.marketdata.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration
public class NewsReadExecutorConfig {

    /**
     * Haber listesi TR çevirisi: sayfa başına çok sayıda dış HTTP çağrısı sırayla yapılınca
     * süre çarpılıyordu. Küçük havuz ile paralel (I/O bound) sınırlı eşzamanlılık.
     */
    @Bean(name = "newsReadExecutor")
    public Executor newsReadExecutor() {
        ThreadFactory tf = (r) -> {
            Thread t = new Thread(r, "news-read");
            t.setDaemon(true);
            return t;
        };
        return Executors.newFixedThreadPool(4, tf);
    }
}
