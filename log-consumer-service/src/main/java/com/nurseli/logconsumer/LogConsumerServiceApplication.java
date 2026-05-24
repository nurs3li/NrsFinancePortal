package com.nurseli.logconsumer;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

/**
 * Log Consumer Service Spring Boot giriş noktası; Kafka consumer'ları ve OpenSearch indexleme bileşenlerini başlatır.
 */
@SpringBootApplication
public class LogConsumerServiceApplication {

    /**
     * {@code init} — Uygulama varsayılan zaman dilimini {@code Europe/Istanbul} olarak ayarlar.
     */
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"));
    }

    /**
     * {@code main} — Spring Boot uygulamasını ayağa kaldırır.
     */
    public static void main(String[] args) {
        SpringApplication.run(LogConsumerServiceApplication.class, args);
    }
}
