package com.nurseli.logconsumer.integration.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Tüm log-consumer integration testleri için paylaşımlı OpenSearch + Redis örneği (sınıflar arası paylaşımlı).
 */
public final class LogConsumerIntegrationContainers {

    public static final GenericContainer<?> OPENSEARCH =
            new GenericContainer<>(DockerImageName.parse("opensearchproject/opensearch:2.11.0"))
                    .withEnv("discovery.type", "single-node")
                    .withEnv("DISABLE_SECURITY_PLUGIN", "true")
                    .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
                    .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
                    .withExposedPorts(9200);

    public static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        OPENSEARCH.start();
        REDIS.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            REDIS.stop();
            OPENSEARCH.stop();
        }));
    }

    private LogConsumerIntegrationContainers() {
    }
}
