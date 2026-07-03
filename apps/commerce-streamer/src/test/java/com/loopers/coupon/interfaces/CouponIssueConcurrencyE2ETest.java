package com.loopers.coupon.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaTopic;
import com.loopers.coupon.domain.Coupon;
import com.loopers.coupon.domain.CouponIssueRequest;
import com.loopers.coupon.domain.CouponIssueRequestStatus;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.infrastructure.CouponIssueRequestJpaRepository;
import com.loopers.coupon.infrastructure.CouponJpaRepository;
import com.loopers.coupon.infrastructure.UserCouponJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 선착순 한도보다 많은 요청이 동시에 들어와도 초과 발급이 없음을 실제 Kafka 파이프라인으로 검증.
 * 핵심: 안전성은 서비스의 락이 아니라 key=couponId 파티션 직렬화에서 온다.
 * latest offset 구독 race 를 피하려 같은 requestId 들을 반복 발행한다(멱등이라 결과 불변).
 */
@SpringBootTest
class CouponIssueConcurrencyE2ETest {

    private static final int LIMIT = 5;
    private static final int REQUESTERS = 10;

    @TestConfiguration
    static class TestKafkaConfig {
        @Bean
        NewTopic couponIssueRequestsTopicForTest() {
            return TopicBuilder.name(KafkaTopic.COUPON_ISSUE_REQUESTS).partitions(3).replicas(1).build();
        }

        @Bean
        KafkaTemplate<String, String> stringKafkaTemplate(KafkaProperties kafkaProperties) {
            Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
        }
    }

    @Autowired
    private KafkaTemplate<String, String> stringKafkaTemplate;
    @Autowired
    private CouponJpaRepository couponJpaRepository;
    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;
    @Autowired
    private CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("선착순 한도 5에 10명이 요청해도 정확히 5명만 발급된다(초과 발급 없음)")
    void givenLimit5_when10Requests_thenExactlyLimitIssued() {
        Long couponId = couponJpaRepository.save(Coupon.createLimited(
                "선착순", CouponType.FIXED, 3_000L, null, ZonedDateTime.now().plusDays(30), (long) LIMIT)).getId();

        List<String> requestIds = new ArrayList<>();
        List<String> payloads = new ArrayList<>();
        for (long userId = 1; userId <= REQUESTERS; userId++) {
            String requestId = UUID.randomUUID().toString();
            couponIssueRequestJpaRepository.save(CouponIssueRequest.pending(requestId, couponId, userId));
            requestIds.add(requestId);
            payloads.add(serialize(new CouponIssueRequestedMessage(requestId, couponId, userId, ZonedDateTime.now())));
        }

        String key = String.valueOf(couponId);
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            payloads.forEach(payload -> stringKafkaTemplate.send(KafkaTopic.COUPON_ISSUE_REQUESTS, key, payload));
            stringKafkaTemplate.flush();
            assertThat(processedCount(requestIds)).isEqualTo(REQUESTERS);
        });

        assertThat(userCouponJpaRepository.countByCouponId(couponId)).isEqualTo(LIMIT);
        long rejected = requestIds.stream()
                .filter(id -> statusOf(id) == CouponIssueRequestStatus.REJECTED)
                .count();
        assertThat(rejected).isEqualTo(REQUESTERS - LIMIT);
    }

    private long processedCount(List<String> requestIds) {
        return requestIds.stream().filter(id -> statusOf(id) != CouponIssueRequestStatus.PENDING).count();
    }

    private CouponIssueRequestStatus statusOf(String requestId) {
        return couponIssueRequestJpaRepository.findByRequestId(requestId).orElseThrow().getStatus();
    }

    private String serialize(CouponIssueRequestedMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
