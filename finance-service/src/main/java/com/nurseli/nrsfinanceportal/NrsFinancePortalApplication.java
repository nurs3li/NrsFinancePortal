package com.nurseli.nrsfinanceportal;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

/**
 * Spring Boot ana uygulama sınıfı; varsayılan zaman dilimini Europe/Istanbul yapar
 * ve Spring Data {@code PageImpl} serileştirmesini VIA_DTO modunda etkinleştirir.
 */
@SpringBootApplication
@EnableScheduling
// PageImpl serialization icin VIA_DTO modu (admin listeleri vb.).
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class NrsFinancePortalApplication {

    /** JVM varsayılan TimeZone'unu Europe/Istanbul olarak ayarlar. */
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"));
    }

    /** Uygulamayı başlatır. */
    public static void main(String[] args) {
        SpringApplication.run(NrsFinancePortalApplication.class, args);
    }
}
