package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponIssueConcurrencyIntegrationTest {

    @Autowired private CouponIssueProcessor couponIssueProcessor;
    @Autowired private CouponRepository couponRepository;
    @Autowired private CouponIssueRequestRepository couponIssueRequestRepository;
    @Autowired private UserCouponRepository userCouponRepository;
    @Autowired private CouponIssueRedisStore couponIssueRedisStore;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("Redis reserved requests are issued exactly once when processed concurrently")
    @Test
    void issuesOnlyRedisReservedRequests_whenProcessedConcurrently() throws InterruptedException {
        // Arrange
        int totalQuantity = 10;
        int requestCount = 30;
        CouponModel coupon = couponRepository.save(new CouponModel(
            "first come coupon", CouponType.FIXED, 10_000, null,
            ZonedDateTime.now().plusDays(1), totalQuantity));

        List<String> requestIds = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            saveReservedRequest(coupon, (long) (i + 1))
                .map(CouponIssueRequest::getRequestId)
                .ifPresent(requestIds::add);
        }

        // Act
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(requestIds.size());
        for (String requestId : requestIds) {
            executor.submit(() -> {
                try {
                    couponIssueProcessor.process(requestId);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        long issuedRequests = requestIds.stream()
            .map(id -> couponIssueRequestRepository.findByRequestId(id).orElseThrow())
            .filter(r -> r.getStatus() == CouponIssueRequest.Status.ISSUED)
            .count();
        long failedRequests = requestIds.stream()
            .map(id -> couponIssueRequestRepository.findByRequestId(id).orElseThrow())
            .filter(r -> r.getStatus() == CouponIssueRequest.Status.FAILED)
            .count();

        assertThat(requestIds).hasSize(totalQuantity);
        assertThat(issuedRequests).isEqualTo(totalQuantity);
        assertThat(failedRequests).isZero();
        assertThat(couponRepository.findById(coupon.getId()).orElseThrow().getIssuedCount()).isEqualTo(totalQuantity);
        assertThat(userCouponRepository.findByCouponId(coupon.getId(), 0, 100)).hasSize(totalQuantity);
    }

    @DisplayName("Redelivered same request is ignored after first issuance")
    @Test
    void processesOnlyOnce_whenSameRequestRedelivered() {
        // Arrange
        CouponModel coupon = couponRepository.save(new CouponModel(
            "first come coupon", CouponType.FIXED, 1_000, null, ZonedDateTime.now().plusDays(1), 5));
        CouponIssueRequest request = saveReservedRequest(coupon, 1L).orElseThrow();

        // Act
        couponIssueProcessor.process(request.getRequestId());
        couponIssueProcessor.process(request.getRequestId());
        couponIssueProcessor.process(request.getRequestId());

        // Assert
        assertThat(couponRepository.findById(coupon.getId()).orElseThrow().getIssuedCount()).isEqualTo(1L);
        assertThat(userCouponRepository.findByCouponId(coupon.getId(), 0, 100)).hasSize(1);
        assertThat(couponIssueRequestRepository.findByRequestId(request.getRequestId()).orElseThrow().getStatus())
            .isEqualTo(CouponIssueRequest.Status.ISSUED);
    }

    @DisplayName("Batch processing issues only Redis reserved requests")
    @Test
    void batchProcessingIssuesOnlyRedisReservedRequests() {
        // Arrange
        int totalQuantity = 10;
        CouponModel coupon = couponRepository.save(new CouponModel(
            "first come batch", CouponType.FIXED, 1_000, null, ZonedDateTime.now().plusDays(1), totalQuantity));

        List<String> requestIds = new ArrayList<>();
        for (int i = 0; i < 29; i++) {
            saveReservedRequest(coupon, (long) (i + 1))
                .map(CouponIssueRequest::getRequestId)
                .ifPresent(requestIds::add);
        }
        requestIds.add(couponIssueRequestRepository.save(CouponIssueRequest.accept(1L, coupon.getId())).getRequestId());

        // Act
        couponIssueProcessor.processBatch(requestIds);
        couponIssueProcessor.processBatch(requestIds);

        // Assert
        long issued = requestIds.stream()
            .map(id -> couponIssueRequestRepository.findByRequestId(id).orElseThrow())
            .filter(r -> r.getStatus() == CouponIssueRequest.Status.ISSUED)
            .count();
        assertThat(issued).isEqualTo(totalQuantity);
        assertThat(couponRepository.findById(coupon.getId()).orElseThrow().getIssuedCount()).isEqualTo(totalQuantity);
        assertThat(userCouponRepository.findByCouponId(coupon.getId(), 0, 100)).hasSize(totalQuantity);

        CouponIssueRequest duplicate = couponIssueRequestRepository
            .findByRequestId(requestIds.get(requestIds.size() - 1)).orElseThrow();
        assertThat(duplicate.getStatus()).isEqualTo(CouponIssueRequest.Status.FAILED);
        assertThat(duplicate.getFailureReason()).isNotBlank();
    }

    @DisplayName("Second request from same user fails when duplicated event reaches processor")
    @Test
    void failsSecondRequest_whenSameUserRequestsTwice() {
        // Arrange
        CouponModel coupon = couponRepository.save(new CouponModel(
            "first come coupon", CouponType.FIXED, 1_000, null, ZonedDateTime.now().plusDays(1), 5));
        CouponIssueRequest first = saveReservedRequest(coupon, 1L).orElseThrow();
        CouponIssueRequest second = couponIssueRequestRepository.save(CouponIssueRequest.accept(1L, coupon.getId()));

        // Act
        couponIssueProcessor.process(first.getRequestId());
        couponIssueProcessor.process(second.getRequestId());

        // Assert
        assertThat(couponIssueRequestRepository.findByRequestId(first.getRequestId()).orElseThrow().getStatus())
            .isEqualTo(CouponIssueRequest.Status.ISSUED);
        CouponIssueRequest failed = couponIssueRequestRepository.findByRequestId(second.getRequestId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(CouponIssueRequest.Status.FAILED);
        assertThat(failed.getFailureReason()).isNotBlank();
        assertThat(couponRepository.findById(coupon.getId()).orElseThrow().getIssuedCount()).isEqualTo(1L);
    }

    private Optional<CouponIssueRequest> saveReservedRequest(CouponModel coupon, Long userId) {
        String requestId = UUID.randomUUID().toString();
        CouponIssueRedisStore.ReservationResult result = couponIssueRedisStore.reserve(
            coupon.getId(),
            userId,
            coupon.getTotalQuantity(),
            requestId
        );
        if (result != CouponIssueRedisStore.ReservationResult.RESERVED) {
            return Optional.empty();
        }
        return Optional.of(couponIssueRequestRepository.save(
            CouponIssueRequest.accept(requestId, userId, coupon.getId())));
    }
}
