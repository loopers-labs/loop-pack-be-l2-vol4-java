package com.loopers.interfaces.consumer;

import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.testcontainers.KafkaTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class DeadLetterTopicE2ETest {

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaTestContainersConfig::getBootstrapServers);
    }

    @Autowired private ProductMetricsRepository metricsRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    @DisplayName("파싱 불가 레코드(독약)는 재시도 없이 catalog-events.DLT 로 격리되고, 이어진 정상 레코드는 정상 처리된다.")
    @Test
    void poisonMessageIsIsolatedToDlt_andValidStillProcessed() {
        // DLT 토픽은 사전 프로비저닝(운영도 동일) — recoverer 의 프로듀서 발행이 안착하도록 미리 만든다.
        createTopic("catalog-events.DLT");

        // 독약: CatalogEventMessage 로 역직렬화 불가한 JSON
        publish("catalog-events", "900", "{ not-a-valid-json ]");
        // 정상: 다른 키(다른 파티션) — 독약 재시도에 막히지 않고 처리되어야 한다
        String valid = "{\"eventId\":\"dlt-ok-1\",\"type\":\"ProductViewed\",\"productId\":901,"
            + "\"likeCount\":0,\"version\":0,\"occurredAt\":\"2026-07-02T00:00:00Z\"}";

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            publish("catalog-events", "901", valid);
            assertThat(metricsRepository.find(901L)).isPresent()
                .get().extracting(m -> m.getViewCount()).isEqualTo(1L);
        });

        // 독약은 (재시도 없이) catalog-events.DLT 로 격리 발행되어 있어야 한다
        List<String> dltValues = drain("catalog-events.DLT", Duration.ofSeconds(30));
        assertThat(dltValues).anyMatch(v -> v.contains("not-a-valid-json"));
    }

    private void createTopic(String topic) {
        try (Admin admin = Admin.create(java.util.Map.of(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaTestContainersConfig.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
        } catch (Exception alreadyExistsOrRace) {
            // 이미 있으면 무시
        }
    }

    private void publish(String topic, String key, String value) {
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaTestContainersConfig.getBootstrapServers());
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(p)) {
            producer.send(new ProducerRecord<>(topic, key, value));
            producer.flush();
        }
    }

    // DLT 토픽을 earliest 부터 폴링해 값들을 수집한다(격리 발행 확인용).
    private List<String> drain(String topic, Duration timeout) {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaTestContainersConfig.getBootstrapServers());
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-verifier-" + topic);
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // DLT 토픽은 recoverer 의 첫 발행 시점에 생성된다 → 기본 5분 메타데이터 주기로는 새 토픽/파티션을
        // 발견하지 못한다. 갱신 주기를 짧게 잡아 늦게 생긴 DLT 파티션을 곧바로 잡도록 한다.
        p.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, 1000);
        List<String> values = new java.util.ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(p)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline && values.isEmpty()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> r : records) {
                    values.add(r.value());
                }
            }
        }
        return values;
    }
}
