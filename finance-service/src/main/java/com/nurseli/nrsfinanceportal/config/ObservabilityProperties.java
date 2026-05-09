package com.nurseli.nrsfinanceportal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.observability")
@Data
public class ObservabilityProperties {

    /**
     * Kapalıyken audit/opensearch/tempo/grafana uçları boş veya devre dışı yanıt verir.
     */
    private boolean enabled = true;

    private final OpenSearch openSearch = new OpenSearch();
    private final Tempo tempo = new Tempo();
    private final Grafana grafana = new Grafana();

    @Data
    public static class OpenSearch {
        /** Örn. http://nrs-opensearch:9200 */
        private String baseUrl = "http://localhost:9200";
        private String indexPattern = "application-logs-*";
        private int requestTimeoutMs = 15000;
    }

    @Data
    public static class Tempo {
        /** Örn. http://nrs-tempo:3200 */
        private String baseUrl = "http://localhost:3200";
        private int requestTimeoutMs = 12000;
    }

    @Data
    public static class Grafana {
        /** Tarayıcıdan açılacak tam kök (örn. http://localhost:3001) */
        private String publicBaseUrl = "http://localhost:3001";
        private String orgId = "1";
        /** Provisioning uid (infra/grafana/provisioning/datasources/tempo.yml) */
        private String tempoDatasourceUid = "nrs-tempo";
    }
}
