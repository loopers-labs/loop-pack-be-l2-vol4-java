package com.loopers.coupon.application;

import com.loopers.coupon.domain.Coupon;
import com.loopers.coupon.domain.CouponIssueRequest;
import com.loopers.coupon.domain.CouponIssueRequestStatus;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.infrastructure.CouponIssueRequestJpaRepository;
import com.loopers.coupon.infrastructure.CouponJpaRepository;
import com.loopers.coupon.infrastructure.UserCouponJpaRepository;
import com.loopers.coupon.interfaces.CouponIssueRequestedMessage;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 원자적 수량 차감이 진짜 동시 부하에서 초과 발급을 막는지 검증(파티션 직렬화 없이 서비스 직접 호출).
 * 수량 100 쿠폰에 서로 다른 1000명이 동시에 신청 → 정확히 100건만 발급.
 */
@SpringBootTest
class CouponIssuanceConcurrencyTest {

    private static final int QUANTITY = 100;
    private static final int REQUESTERS = 1000;

    private final CouponIssuanceService couponIssuanceService;
    private final CouponJpaRepository couponJpaRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponIssuanceConcurrencyTest(CouponIssuanceService couponIssuanceService,
                                  CouponJpaRepository couponJpaRepository,
                                  UserCouponJpaRepository userCouponJpaRepository,
                                  CouponIssueRequestJpaRepository couponIssueRequestJpaRepository,
                                  DatabaseCleanUp databaseCleanUp) {
        this.couponIssuanceService = couponIssuanceService;
        this.couponJpaRepository = couponJpaRepository;
        this.userCouponJpaRepository = userCouponJpaRepository;
        this.couponIssueRequestJpaRepository = couponIssueRequestJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("수량 100 쿠폰에 서로 다른 1000명이 동시에 신청해도 정확히 100건만 발급된다(초과 0)")
    void given100Quantity_when1000ConcurrentRequests_thenExactly100Issued() throws Exception {
        Long couponId = couponJpaRepository.save(Coupon.createLimited(
                "선착순", CouponType.FIXED, 3_000L, null, ZonedDateTime.now().plusDays(30), (long) QUANTITY)).getId();

        List<CouponIssueRequestedMessage> messages = new ArrayList<>();
        for (long userId = 1; userId <= REQUESTERS; userId++) {
            String requestId = UUID.randomUUID().toString();
            couponIssueRequestJpaRepository.save(CouponIssueRequest.pending(requestId, couponId, userId));
            messages.add(new CouponIssueRequestedMessage(requestId, couponId, userId, ZonedDateTime.now()));
        }

        // 1000개를 한 번에 발사하되, DB 접근은 세마포어로 묶는다(실제 시스템의 유한한 커넥션 풀처럼).
        // 초과 발급 방지는 원자 UPDATE 책임이라 동시 접근 수와 무관하게 성립해야 한다.
        CountDownLatch go = new CountDownLatch(1);
        Semaphore dbPermits = new Semaphore(32);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (CouponIssueRequestedMessage message : messages) {
                futures.add(pool.submit(() -> {
                    go.await();
                    dbPermits.acquire();
                    try {
                        couponIssuanceService.issue(message);
                    } finally {
                        dbPermits.release();
                    }
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }

        long issued = userCouponJpaRepository.countByCouponId(couponId);
        long rejected = messages.stream()
                .filter(m -> couponIssueRequestJpaRepository.findByRequestId(m.requestId())
                        .orElseThrow().getStatus() == CouponIssueRequestStatus.REJECTED)
                .count();

        assertThat(issued).isEqualTo(QUANTITY);              // 정확히 100건
        assertThat(rejected).isEqualTo(REQUESTERS - QUANTITY); // 나머지 900건 거절
    }
}
