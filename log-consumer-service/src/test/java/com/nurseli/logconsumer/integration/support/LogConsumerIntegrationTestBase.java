package com.nurseli.logconsumer.integration.support;

import org.junit.jupiter.api.function.Executable;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * log-consumer integration test tabanı: EmbeddedKafka + OpenSearch + Redis (Testcontainers), MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integrationtest")
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "application-logs",
                "finance.transaction.created",
                "finance.transaction.reversed"
        })
public abstract class LogConsumerIntegrationTestBase {

    public static final String APPLICATION_LOGS_TOPIC = "application-logs";

    @DynamicPropertySource
    static void registerContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers",
                () -> System.getProperty("spring.embedded.kafka.brokers"));
        registry.add("spring.data.redis.host", LogConsumerIntegrationContainers.REDIS::getHost);
        registry.add("spring.data.redis.port", () -> LogConsumerIntegrationContainers.REDIS.getMappedPort(6379));
        registry.add("opensearch.host", LogConsumerIntegrationContainers.OPENSEARCH::getHost);
        registry.add("opensearch.port", () -> LogConsumerIntegrationContainers.OPENSEARCH.getMappedPort(9200));
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected RestHighLevelClient opensearchClient;

    @Autowired
    protected KafkaTemplate<String, String> kafkaTemplate;

    protected void publishApplicationLog(String jsonPayload) {
        kafkaTemplate.send(APPLICATION_LOGS_TOPIC, jsonPayload);
    }

    protected SearchHit[] searchLogsByCorrelationId(String indexName, String correlationId) throws IOException {
        SearchRequest request = new SearchRequest(indexName);
        request.source(new SearchSourceBuilder()
                .query(QueryBuilders.termQuery("correlationId", correlationId))
                .size(10));
        SearchResponse response = opensearchClient.search(request, RequestOptions.DEFAULT);
        return response.getHits().getHits();
    }

    protected Map<String, Object> awaitIndexedLog(String indexName, String correlationId, Duration timeout)
            throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        IOException lastIo = null;
        AssertionError lastAssertion = null;

        while (System.nanoTime() < deadline) {
            try {
                SearchHit[] hits = searchLogsByCorrelationId(indexName, correlationId);
                if (hits.length >= 1) {
                    return hits[0].getSourceAsMap();
                }
                lastAssertion = new AssertionError("Expected indexed log for correlationId=" + correlationId);
            } catch (IOException e) {
                lastIo = e;
            }

            sleepQuietly(250);
        }

        if (lastIo != null) {
            throw lastIo;
        }
        if (lastAssertion != null) {
            throw lastAssertion;
        }
        fail("Timed out waiting for indexed log correlationId=" + correlationId);
        return Map.of();
    }

    protected void awaitUntil(Duration timeout, Executable assertion) {
        long deadline = System.nanoTime() + timeout.toNanos();
        Throwable last = null;

        while (System.nanoTime() < deadline) {
            try {
                assertDoesNotThrow(assertion);
                return;
            } catch (Throwable t) {
                last = t;
                sleepQuietly(250);
            }
        }

        if (last instanceof AssertionError ae) {
            throw ae;
        }
        fail("Condition not met within " + timeout, last);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting for async condition");
        }
    }
}
