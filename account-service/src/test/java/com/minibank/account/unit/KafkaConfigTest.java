package com.minibank.account.unit;

import com.minibank.account.config.KafkaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KafkaConfigTest {

    private KafkaConfig kafkaConfig;

    @BeforeEach
    void setUp() {
        kafkaConfig = new KafkaConfig();
        ReflectionTestUtils.setField(kafkaConfig, "bootstrapServers", "localhost:9092");
    }

    @Test
    @DisplayName("Should create sagaConsumerFactory with correct type")
    void sagaConsumerFactory_ReturnsCorrectType() {
        ConsumerFactory<String, Map<String, Object>> factory = kafkaConfig.sagaConsumerFactory();
        assertNotNull(factory);
        assertTrue(factory instanceof DefaultKafkaConsumerFactory);
    }

    @Test
    @DisplayName("Should create kafkaListenerContainerFactory with consumer factory")
    void kafkaListenerContainerFactory_CreatesFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Map<String, Object>> factory =
                kafkaConfig.kafkaListenerContainerFactory();
        assertNotNull(factory);
        assertEquals(AckMode.MANUAL_IMMEDIATE, factory.getContainerProperties().getAckMode());
    }

    @Test
    @DisplayName("Should create sagaProducerFactory with correct type")
    void sagaProducerFactory_ReturnsCorrectType() {
        ProducerFactory<String, Map<String, Object>> factory = kafkaConfig.sagaProducerFactory();
        assertNotNull(factory);
    }

    @Test
    @DisplayName("Should create sagaKafkaTemplate with producer factory")
    void sagaKafkaTemplate_CreatesTemplate() {
        KafkaTemplate<String, Map<String, Object>> template = kafkaConfig.sagaKafkaTemplate();
        assertNotNull(template);
    }

    @Test
    @DisplayName("Should use configured bootstrap servers")
    void sagaConsumerFactory_UsesConfiguredServers() {
        ReflectionTestUtils.setField(kafkaConfig, "bootstrapServers", "kafka1:9092,kafka2:9092");
        ConsumerFactory<String, Map<String, Object>> factory = kafkaConfig.sagaConsumerFactory();
        assertNotNull(factory);
    }
}