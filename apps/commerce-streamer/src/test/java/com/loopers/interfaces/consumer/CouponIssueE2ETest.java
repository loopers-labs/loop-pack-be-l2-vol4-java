package com.loopers.interfaces.consumer;

import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.testcontainers.KafkaTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class CouponIssueE2ETest {

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaTestContainersConfig::getBootstrapServers);
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long couponId;

    @BeforeEach
    void setUp() {
        jdbc.update("""
            INSERT INTO coupon (name, type, discount_value, min_order_amount, expired_at, quantity, issued_count, created_at, updated_at)
            VALUES ('선착순', 'FIXED', 1000, NULL, DATE_ADD(NOW(), INTERVAL 1 DAY), 100, 0, NOW(), NOW())
            """);
        couponId = jdbc.queryForObject("SELECT id FROM coupon ORDER BY id DESC LIMIT 1", Long.class);
    }

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    @DisplayName("coupon-issue-requests 발행 → consumer가 user_coupon 발급 + result ISSUED.")
    @Test
    void issuesEndToEnd() {
        String payload = "{\"requestId\":\"req-e2e\",\"couponId\":" + couponId
            + ",\"userId\":7,\"occurredAt\":\"2026-07-02T00:00:00Z\"}";

        // consumer group(coupon-issue)이 auto.offset.reset=latest 로 첫 rebalance 를 마치기 전에
        // 발행하면 그 메시지는 건너뛰어질 수 있어(신규 그룹 join race), 소비될 때까지 재발행한다.
        // CouponIssueProcessor 의 event_handled 멱등 가드 덕분에 동일 requestId 재발행은 안전하다.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            publish("coupon-issue-requests", String.valueOf(couponId), payload);
            Long issued = jdbc.queryForObject(
                "SELECT COUNT(*) FROM coupon_issue_result WHERE request_id='req-e2e' AND status=?",
                Long.class, CouponIssueStatus.ISSUED.name());
            assertThat(issued).isEqualTo(1L);
        });
        Long userCoupons = jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_coupon WHERE coupon_id=? AND user_id=7", Long.class, couponId);
        assertThat(userCoupons).isEqualTo(1L);
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
}
