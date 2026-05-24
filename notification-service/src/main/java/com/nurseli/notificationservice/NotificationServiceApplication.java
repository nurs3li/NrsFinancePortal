package com.nurseli.notificationservice;

import com.nurseli.notificationservice.config.NotificationEmailProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

import java.util.TimeZone;

/**
 * Notification-service Spring Boot uygulama giriş noktası.
 * <p>
 * Spring Data 3.3+ ile {@code PageImpl} ham JSON'a serialize edildiğinde kararsız sayılır ve WARN
 * üretir. {@code VIA_DTO} modu cevap shape'ini stabilize eder:
 * {@code { content, page: { size, number, totalElements, totalPages } }}.
 * Frontend (Trade, AdminTasks, Notifications) bu yapıya göre güncellendi;
 * top-level totalPages/totalElements yerine {@code page.*} alanları kullanılır.
 */
@SpringBootApplication
@EnableConfigurationProperties(NotificationEmailProperties.class)
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class NotificationServiceApplication {

	/**
	 * {@code init} — Varsayılan JVM zaman dilimini Europe/Istanbul olarak ayarlar.
	 */
	@PostConstruct
	public void init() {
		TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"));
	}

	/**
	 * {@code main} — Uygulamayı başlatır.
	 */
	public static void main(String[] args) {
		SpringApplication.run(NotificationServiceApplication.class, args);
	}
}
