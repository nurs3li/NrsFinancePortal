package com.nurseli.nrsfinanceportal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.notification")
public class NotificationClientProperties {
    private String baseUrl = "http://localhost:8089";
}
