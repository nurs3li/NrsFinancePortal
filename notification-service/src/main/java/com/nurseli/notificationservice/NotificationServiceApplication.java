package com.nurseli.notificationservice;

import com.nurseli.notificationservice.config.NotificationEmailProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties(NotificationEmailProperties.class)
// Spring Data 3.3+: PageImpl'i ham JSON'a serialize etmek "kararsiz" sayiliyor ve WARN
// uretiyor. VIA_DTO modu ile cevap shape'i stabilize edilir: { content, page: { size,
// number, totalElements, totalPages } }. Frontend (Trade, AdminTasks, Notifications)
// bu yeni yapiya gore guncellendi -- top-level totalPages/totalElements yerine
// page.* alanlari kullaniliyor.
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class NotificationServiceApplication {

	@PostConstruct
	public void init() {
		TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"));
	}

	public static void main(String[] args) {
		SpringApplication.run(NotificationServiceApplication.class, args);
	}
}