package com.nurseli.nrsfinanceportal.integration.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Tüm finance-service integration testleri için paylaşımlı Postgres + Redis örneği.
 */
public final class FinanceIntegrationContainers {

    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("nrs_finance_it")
            .withUsername("nrs")
            .withPassword("nrs123")
            .withCommand("postgres", "-c", "max_connections=200");

    public static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            REDIS.stop();
            POSTGRES.stop();
        }));
    }

    private FinanceIntegrationContainers() {
    }
}
