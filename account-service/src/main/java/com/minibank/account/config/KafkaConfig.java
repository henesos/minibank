package com.minibank.account.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Configuration for Account Service.
 *
 * <h3>H4 (ADR-014): Manual Ack Mode</h3>
 * <p>Consumer container factory is configured with {@link ContainerProperties.AckMode#MANUAL}
 * so that the Kafka message offset is committed only after the DB transaction
 * successfully commits. On rollback the offset is NOT committed and Kafka
 * redelivers the message.
 *
 * <h3>H8 (ADR-015): BigDecimal Preservation</h3>
 * <p>Consumer {@link ObjectMapper} is configured with
 * {@link DeserializationFeature#USE_BIG_DECIMAL_FOR_FLOATS} so that JSON
 * floating-point numbers are deserialized as {@link java.math.BigDecimal}
 * instead of {@code Double}. This eliminates precision loss at the
 * deserialization layer, complementing the String-based conversion in
 * {@link com.minibank.account.kafka.SagaCommandConsumer#parseAmount}.
 *
 * <p>Consumes from: saga-commands (for DEBIT_REQUEST, CREDIT_REQUEST)
 * <p>Produces to: saga-events (for DEBIT_SUCCESS/FAILURE, CREDIT_SUCCESS/FAILURE)
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Consumer Factory for saga command messages.
     *
     * <p>Uses a custom {@link ObjectMapper} with {@code USE_BIG_DECIMAL_FOR_FLOATS}
     * to preserve financial precision (ADR-015).
     */
    @Bean
    public ConsumerFactory<String, Map<String, Object>> sagaConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "account-service-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // ADR-015: Custom ObjectMapper that deserializes floats as BigDecimal
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Create JsonDeserializer with the custom ObjectMapper
        @SuppressWarnings("unchecked")
        JsonDeserializer<Map<String, Object>> deserializer =
                new JsonDeserializer<>((Class<Map<String, Object>>) (Class<?>) Map.class, objectMapper);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                deserializer
        );
    }

    /**
     * Kafka Listener Container Factory.
     *
     * <p>ADR-014: Configured with {@link ContainerProperties.AckMode#MANUAL}
     * so that {@link org.springframework.kafka.support.Acknowledgment} must be
     * invoked explicitly by the consumer. Combined with
     * {@link org.springframework.transaction.support.TransactionSynchronization},
     * the ack is performed only after the DB transaction commits.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(sagaConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }

    /**
     * Producer Factory for SagaEvent responses.
     */
    @Bean
    public ProducerFactory<String, Map<String, Object>> sagaProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Kafka Template for producing saga events.
     */
    @Bean
    public KafkaTemplate<String, Map<String, Object>> sagaKafkaTemplate() {
        return new KafkaTemplate<>(sagaProducerFactory());
    }
}
