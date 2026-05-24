package com.nurseli.logconsumer.config;

import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenSearch {@link RestHighLevelClient} bağlantı bean'i; host/port {@code application.yml} üzerinden okunur.
 */
@Configuration
public class OpenSearchConfig {

    @Value("${opensearch.host:localhost}")
    private String host;

    @Value("${opensearch.port:9200}")
    private int port;

    /**
     * {@code opensearchClient} — HTTP üzerinden OpenSearch cluster'a bağlanan high-level client üretir.
     */
    @Bean
    public RestHighLevelClient opensearchClient() {
        return new RestHighLevelClient(
                RestClient.builder(new HttpHost(host, port, "http"))
        );
    }
}
