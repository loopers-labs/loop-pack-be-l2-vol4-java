package com.loopers.interfaces.consumer.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 Kafka 브로커(Testcontainers)에 발행해 commerce-api 자신의 컨슈머(CouponIssueRequestConsumer)가
// coupon-issue-requests를 소비하고 CouponIssueProcessor까지 위임하는 배선(wiring)만 검증한다.
// 발급 로직 자체의 세부 시나리오는 CouponIssueProcessorIntegrationTest가 담당한다.
@SpringBootTest
class CouponIssueRequestConsumerIntegrationTest {

    private static final String TOPIC = "coupon-issue-requests";
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private KafkaTemplate<String, String> stringKafkaTemplate;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private CouponIssueRequestRepository couponIssueRequestRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void publish(String eventId, String requestId, Long couponTemplateId, Long userId) throws Exception {
        String envelope = objectMapper.writeValueAsString(Map.of(
                "eventId", eventId,
                "aggregateType", "CouponIssueRequest",
                "aggregateId", String.valueOf(couponTemplateId),
                "eventType", "COUPON_ISSUE_REQUESTED",
                "payload", Map.of("requestId", requestId, "couponTemplateId", couponTemplateId, "userId", userId)
        ));
        stringKafkaTemplate.send(TOPIC, String.valueOf(couponTemplateId), envelope).get();
    }

    private CouponIssueRequestModel awaitProcessed(String requestId) throws InterruptedException {
        Instant deadline = Instant.now().plus(POLL_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            CouponIssueRequestModel request = couponIssueRequestRepository.findByRequestId(requestId).orElseThrow();
            if (request.getStatus() != CouponIssueRequestStatus.PENDING) {
                return request;
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("발급 요청 처리를 기다리는 동안 타임아웃 발생. requestId=" + requestId);
    }

    @DisplayName("coupon-issue-requests 토픽에 메시지를 발행할 때,")
    @Nested
    class Listen {

        @DisplayName("컨슈머가 소비해 발급 요청 상태를 ISSUED로 반영한다.")
        @Test
        void marksRequestIssued_whenMessagePublished() throws Exception {
            // given
            CouponTemplateModel template = couponTemplateRepository.save(new CouponTemplateModel(
                    "선착순 쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000), null,
                    ZonedDateTime.now().plusDays(30), 10));
            String requestId = UUID.randomUUID().toString();
            couponIssueRequestRepository.save(new CouponIssueRequestModel(requestId, template.getId(), 1L));

            // when
            publish(requestId, requestId, template.getId(), 1L);

            // then
            CouponIssueRequestModel result = awaitProcessed(requestId);
            assertThat(result.getStatus()).isEqualTo(CouponIssueRequestStatus.ISSUED);
        }
    }
}
