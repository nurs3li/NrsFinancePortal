package com.nurseli.notificationservice.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Plugin(name = "KafkaLog", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class KafkaLogAppender extends AbstractAppender {

    private static final String DEFAULT_TOPIC = "application-logs";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final String topic;
    private final String bootstrapServers;
    private final String serviceName;
    private volatile KafkaProducer<String, String> producer;

    protected KafkaLogAppender(String name, Filter filter, String topic, String bootstrapServers, String serviceName) {
        super(name, filter, null, true, Property.EMPTY_ARRAY);
        this.topic = topic != null && !topic.isBlank() ? topic : DEFAULT_TOPIC;
        this.bootstrapServers = bootstrapServers != null && !bootstrapServers.isBlank() ? bootstrapServers : "localhost:9092";
        this.serviceName = serviceName != null && !serviceName.isBlank() ? serviceName : "unknown";
    }

    @Override
    public void start() {
        super.start();
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        producer = new KafkaProducer<>(props);
    }

    @Override
    public void stop() {
        if (producer != null) {
            producer.close();
        }
        super.stop();
    }

    @Override
    public void append(LogEvent event) {
        if (producer == null) return;
        try {
            Map<String, Object> doc = new HashMap<>();
            doc.put("timestamp", formatTimestamp(event.getInstant()));
            doc.put("level", event.getLevel() != null ? event.getLevel().name() : null);
            doc.put("serviceName", serviceName);
            doc.put("message", event.getMessage() != null ? event.getMessage().getFormattedMessage() : null);
            doc.put("logger", event.getLoggerName());
            doc.put("thread", event.getThreadName());

            ReadOnlyStringMap ctx = event.getContextData();
            if (ctx != null && ctx.containsKey("correlationId")) {
                doc.put("correlationId", ctx.getValue("correlationId"));
            }

            ThrowableProxy tp = event.getThrownProxy();
            if (tp != null) {
                doc.put("exception", tp.getName());
                doc.put("stackTrace", tp.getExtendedStackTrace());
            }

            String json = MAPPER.writeValueAsString(doc);
            producer.send(new ProducerRecord<>(topic, null, json));
        } catch (Exception e) {
            // Kafka'ya yazamazsak sessizce geç
        }
    }

    private String formatTimestamp(org.apache.logging.log4j.core.time.Instant instant) {
        if (instant == null) return null;
        try {
            long epochMillis;
            try {
                epochMillis = instant.getEpochMillisecond();
            } catch (NoSuchMethodError e) {
                long sec = instant.getEpochSecond();
                int nano = instant.getNanoOfSecond();
                epochMillis = sec * 1000L + nano / 1_000_000;
            }
            return java.time.Instant.ofEpochMilli(epochMillis).toString();
        } catch (Exception e) {
            return java.time.Instant.now().toString();
        }
    }

    @PluginFactory
    public static KafkaLogAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter,
            @PluginAttribute("topic") String topic,
            @PluginAttribute("bootstrapServers") String bootstrapServers,
            @PluginAttribute("serviceName") String serviceName) {
        if (name == null) name = "KafkaLog";
        return new KafkaLogAppender(name, filter, topic, bootstrapServers, serviceName);
    }
}