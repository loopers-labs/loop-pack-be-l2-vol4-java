package com.loopers.interfaces.consumer;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponQuota;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.coupon.CouponQuotaJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CouponIssueConcurrencyTest {

    private static final Acknowledgment NO_OP_ACK = () -> { };

    private final CouponIssueRequestsConsumer consumer;
    private final CouponQuotaJpaRepository couponQuotaJpaRepository;
    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponIssueConcurrencyTest(
        CouponIssueRequestsConsumer consumer,
        CouponQuotaJpaRepository couponQuotaJpaRepository,
        CouponIssueRequestJpaRepository couponIssueRequestJpaRepository,
        UserCouponJpaRepository userCouponJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.consumer = consumer;
        this.couponQuotaJpaRepository = couponQuotaJpaRepository;
        this.couponIssueRequestJpaRepository = couponIssueRequestJpaRepository;
        this.userCouponJpaRepository = userCouponJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("한도 N 쿠폰에 N+k 명이 동시에 발급 요청해도, consumer 경유 e2e 로 정확히 N 명만 발급되고 초과 발급은 없다.")
    @Test
    void issuesExactlyLimit_whenConcurrentRequestsExceedQuota() throws Exception {
        // given : 한도 50, 서로 다른 유저 120명의 PENDING 발급 요청
        int limit = 50;
        int attempts = 120;
        Long policyId = couponQuotaJpaRepository.save(new CouponQuota((long) limit)).getId();

        List<long[]> works = new ArrayList<>();   // [userId, requestId]
        for (int i = 0; i < attempts; i++) {
            long userId = i + 1;
            Long requestId = couponIssueRequestJpaRepository.save(CouponIssueRequest.pending(userId, policyId)).getId();
            works.add(new long[]{userId, requestId});
        }

        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        // when : 모든 스레드가 동시에 같은 쿠폰을 경합해 소비한다
        for (int i = 0; i < attempts; i++) {
            long userId = works.get(i)[0];
            long requestId = works.get(i)[1];
            String eventId = "evt-" + i;
            futures.add(pool.submit(() -> {
                startGate.await();
                consumer.consume(List.of(record(eventId, requestId, userId, policyId)), NO_OP_ACK);
                return null;
            }));
        }
        startGate.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        // then : 원자적 한도 차감 덕에 정확히 50명만 발급, 초과 0
        assertAll(
            () -> assertThat(userCouponJpaRepository.count()).isEqualTo((long) limit),
            () -> assertThat(couponQuotaJpaRepository.findById(policyId).orElseThrow().getIssuedCount()).isEqualTo((long) limit)
        );
    }

    private ConsumerRecord<String, byte[]> record(String eventId, long requestId, long userId, Long couponPolicyId) {
        String json = """
            {"eventId":"%s","eventType":"COUPON_ISSUE_REQUESTED","aggregateId":%d,
             "data":{"requestId":%d,"userId":%d,"couponPolicyId":%d,"type":"FIXED",
                     "discountValue":3000,"minOrderAmount":10000,"expiredAt":"2099-12-31T23:59:59+09:00"}}
            """.formatted(eventId, couponPolicyId, requestId, userId, couponPolicyId);
        return new ConsumerRecord<>("coupon-issue-requests", 0, 0L, String.valueOf(couponPolicyId),
            json.getBytes(StandardCharsets.UTF_8));
    }
}
