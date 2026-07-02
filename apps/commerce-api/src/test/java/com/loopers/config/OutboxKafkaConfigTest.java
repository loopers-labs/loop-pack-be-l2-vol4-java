package com.loopers.config;

import com.loopers.testcontainers.KafkaTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OutboxKafkaConfigTest {

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaTestContainersConfig::getBootstrapServers);
    }

    @Autowired private KafkaTemplate<String, String> outboxKafkaTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    @DisplayName("outboxKafkaTemplate 로 발행한 메시지를 컨슈머가 그대로 받는다.")
    @Test
    void publishesAndConsumes() throws Exception {
        String marker = "marker-" + UUID.randomUUID();
        outboxKafkaTemplate.send(OutboxKafkaConfig.CATALOG_EVENTS, "1", "{\"marker\":\"" + marker + "\"}").get();

        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            consumer.subscribe(List.of(OutboxKafkaConfig.CATALOG_EVENTS));
            assertThat(pollUntilFound(consumer, marker)).isTrue();
        }
    }

    private KafkaConsumer<String, String> newConsumer() {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaTestContainersConfig.getBootstrapServers());
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "test-verify-" + UUID.randomUUID());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(p);
    }

    private boolean pollUntilFound(KafkaConsumer<String, String> consumer, String marker) {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            var records = consumer.poll(Duration.ofMillis(500));
            for (var r : records) {
                if (r.value().contains(marker)) {
                    return true;
                }
            }
        }
        return false;
    }
}
