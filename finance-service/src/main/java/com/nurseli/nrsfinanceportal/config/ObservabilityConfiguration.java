package com.nurseli.nrsfinanceportal.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityConfiguration {

    private static String trimSlash(String url) {
        if (url == null) return "";
        String t = url.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    @Bean
    @Qualifier("opensearchHttp")
    public RestClient openSearchRestClient(ObservabilityProperties props) {
        String base = trimSlash(props.getOpenSearch().getBaseUrl());
        JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory();
        rf.setReadTimeout(props.getOpenSearch().getRequestTimeoutMs());
        return RestClient.builder()
                .baseUrl(base.isEmpty() ? "http://localhost:9200" : base)
                .requestFactory(rf)
                .build();
    }

    @Bean
    @Qualifier("tempoHttp")
    public RestClient tempoRestClient(ObservabilityProperties props) {
        String base = trimSlash(props.getTempo().getBaseUrl());
        JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory();
        rf.setReadTimeout(props.getTempo().getRequestTimeoutMs());
        return RestClient.builder()
                .baseUrl(base.isEmpty() ? "http://localhost:3200" : base)
                .requestFactory(rf)
                .build();
    }
}
