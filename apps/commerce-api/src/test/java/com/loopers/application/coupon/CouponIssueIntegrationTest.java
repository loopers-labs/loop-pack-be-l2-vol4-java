package com.loopers.application.coupon;

import com.loopers.application.outbox.OutboxRecorder;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.Discount;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.outbox.OutboxEventJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponIssueIntegrationTest {

    private static final LocalDateTime VALID_EXPIRED_AT = LocalDateTime.of(2099, 12, 31, 23, 59, 59);

    @Autowired
    private CouponFacade couponFacade;
    @Autowired
    private CouponIssueProcessor couponIssueProcessor;
    @Autowired
    private CouponIssueRequestRepository couponIssueRequestRepository;
    @Autowired
    private CouponJpaRepository couponJpaRepository;
    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;
    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Coupon saveCoupon(Long totalQuantity) {
        return couponJpaRepository.save(new Coupon("선착순 쿠폰",
            new Discount(CouponType.FIXED, 1000), null, VALID_EXPIRED_AT, totalQuantity));
    }

    private CouponIssueRequest saveRequest(Long userId, Long couponId) {
        return couponIssueRequestRepository.save(
            new CouponIssueRequest(UUID.randomUUID().toString(), couponId, userId));
    }

    @DisplayName("발급 요청을 접수하면, ")
    @Nested
    class RequestIssue {
        @DisplayName("PENDING 요청이 저장되고 coupon-issue-requests 로 향하는 outbox 행이 함께 기록된다.")
        @Test
        void savesPendingRequestAndOutboxEvent() {
            Coupon coupon = saveCoupon(10L);

            CouponIssueRequestInfo info = couponFacade.requestIssue(1L, coupon.getId());

            assertThat(info.status()).isEqualTo(CouponIssueRequestStatus.PENDING);
            assertThat(couponIssueRequestRepository.findByRequestId(info.requestId())).isPresent();
            List<OutboxEvent> events = outboxEventJpaRepository.findAll();
            assertThat(events).anySatisfy(event -> {
                assertThat(event.getTopic()).isEqualTo(OutboxRecorder.COUPON_ISSUE_TOPIC);
                assertThat(event.getEventType()).isEqualTo("CouponIssueRequestedEvent");
                assertThat(event.getPartitionKey()).isEqualTo(String.valueOf(coupon.getId()));
            });
        }
    }

    @DisplayName("발급 요청을 처리하면, ")
    @Nested
    class Process {
        @DisplayName("수량이 남아 있으면 발급되고, 요청은 ISSUED 로 확정된다.")
        @Test
        void issuesCoupon_whenQuantityRemains() {
            Coupon coupon = saveCoupon(10L);
            CouponIssueRequest request = saveRequest(1L, coupon.getId());

            couponIssueProcessor.process(request.getRequestId());

            CouponIssueRequest processed = couponIssueRequestRepository.findByRequestId(request.getRequestId()).orElseThrow();
            assertThat(processed.getStatus()).isEqualTo(CouponIssueRequestStatus.ISSUED);
            assertThat(processed.getIssuedUserCouponId()).isNotNull();
            assertThat(couponJpaRepository.findById(coupon.getId()).orElseThrow().getIssuedQuantity()).isEqualTo(1L);
            assertThat(userCouponJpaRepository.existsByUserIdAndCouponId(1L, coupon.getId())).isTrue();
        }

        @DisplayName("수량이 소진되었으면 요청은 REJECTED 로 확정된다.")
        @Test
        void rejectsRequest_whenQuantityExhausted() {
            Coupon coupon = saveCoupon(1L);
            couponIssueProcessor.process(saveRequest(1L, coupon.getId()).getRequestId());

            CouponIssueRequest second = saveRequest(2L, coupon.getId());
            couponIssueProcessor.process(second.getRequestId());

            CouponIssueRequest processed = couponIssueRequestRepository.findByRequestId(second.getRequestId()).orElseThrow();
            assertThat(processed.getStatus()).isEqualTo(CouponIssueRequestStatus.REJECTED);
            assertThat(processed.getReason()).contains("소진");
        }

        @DisplayName("이미 발급받은 유저의 요청은 REJECTED 로 확정된다.")
        @Test
        void rejectsRequest_whenUserAlreadyIssued() {
            Coupon coupon = saveCoupon(10L);
            couponIssueProcessor.process(saveRequest(1L, coupon.getId()).getRequestId());

            CouponIssueRequest duplicate = saveRequest(1L, coupon.getId());
            couponIssueProcessor.process(duplicate.getRequestId());

            CouponIssueRequest processed = couponIssueRequestRepository.findByRequestId(duplicate.getRequestId()).orElseThrow();
            assertThat(processed.getStatus()).isEqualTo(CouponIssueRequestStatus.REJECTED);
            assertThat(processed.getReason()).contains("이미");
            assertThat(couponJpaRepository.findById(coupon.getId()).orElseThrow().getIssuedQuantity()).isEqualTo(1L);
        }

        @DisplayName("같은 요청이 두 번 처리되어도(중복 배달), 발급은 한 번만 반영된다. (멱등)")
        @Test
        void processesOnlyOnce_whenSameRequestDeliveredTwice() {
            Coupon coupon = saveCoupon(10L);
            CouponIssueRequest request = saveRequest(1L, coupon.getId());

            couponIssueProcessor.process(request.getRequestId());
            couponIssueProcessor.process(request.getRequestId());

            assertThat(couponJpaRepository.findById(coupon.getId()).orElseThrow().getIssuedQuantity()).isEqualTo(1L);
            assertThat(userCouponJpaRepository.countByCouponId(coupon.getId())).isEqualTo(1L);
        }
    }

    @DisplayName("수량 10장에 20명이 동시에 처리되어도, ")
    @Nested
    class Concurrency {
        @DisplayName("초과 발급 없이 정확히 10명만 발급되고 나머지는 거절된다.")
        @Test
        void neverIssuesOverQuantity_whenProcessedConcurrently() throws Exception {
            Coupon coupon = saveCoupon(10L);
            int userCount = 20;
            List<CouponIssueRequest> requests = new java.util.ArrayList<>();
            for (long userId = 1; userId <= userCount; userId++) {
                requests.add(saveRequest(userId, coupon.getId()));
            }
            ExecutorService executor = Executors.newFixedThreadPool(userCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(userCount);

            for (CouponIssueRequest request : requests) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        couponIssueProcessor.process(request.getRequestId());
                    } catch (Throwable ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            long issued = requests.stream()
                .map(request -> couponIssueRequestRepository.findByRequestId(request.getRequestId()).orElseThrow())
                .filter(request -> request.getStatus() == CouponIssueRequestStatus.ISSUED)
                .count();
            assertThat(issued).isEqualTo(10L);
            assertThat(couponJpaRepository.findById(coupon.getId()).orElseThrow().getIssuedQuantity()).isEqualTo(10L);
            assertThat(userCouponJpaRepository.countByCouponId(coupon.getId())).isEqualTo(10L);
        }
    }
}
