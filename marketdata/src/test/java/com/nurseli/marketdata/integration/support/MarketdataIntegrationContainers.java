package com.nurseli.marketdata.integration.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Tüm marketdata integration testleri için tek Postgres + Redis örneği (sınıflar arası paylaşımlı).
 */
public final class MarketdataIntegrationContainers {

    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("nrs_market_it")
            .withUsername("nrs")
            .withPassword("nrs123");

    public static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            REDIS.stop();
            POSTGRES.stop();
        }));
    }

    private MarketdataIntegrationContainers() {
    }
}
