package com.minibank.transaction.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

import com.minibank.transaction.saga.SagaEvent;

/**
 * Kafka Configuration for Transaction Service.
 */
@Configuration
public class KafkaConfig {

    private static final int PRODUCER_RETRIES = 3;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Producer configuration.
     */
    @Bean
    public ProducerFactory<String, SagaEvent> sagaEventProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, PRODUCER_RETRIES);
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * Kafka template for producing events.
     */
    @Bean
    public KafkaTemplate<String, SagaEvent> sagaEventKafkaTemplate() {
        return new KafkaTemplate<>(sagaEventProducerFactory());
    }

    /**
     * Consumer configuration.
     */
    @Bean
    public ConsumerFactory<String, SagaEvent> sagaEventConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(JsonDeserializer.TRUSTED_PACKAGES,
                "com.minibank.transaction.saga,java.util,java.lang");
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, SagaEvent.class);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Listener container factory.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SagaEvent> sagaEventListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, SagaEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(sagaEventConsumerFactory());
        return factory;
    }
}
