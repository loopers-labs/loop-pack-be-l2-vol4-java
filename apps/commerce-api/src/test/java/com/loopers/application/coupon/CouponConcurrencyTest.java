package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.infrastructure.coupon.CouponEntity;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.IssuedCouponEntity;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class CouponConcurrencyTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일 유저가 동일 발급 쿠폰을 동시에 사용 요청하면, 1건만 성공하고 나머지는 CONFLICT 예외가 발생한다.")
    @Test
    void usesOnlyOnce_whenConcurrentRequestsForSameIssuedCoupon() throws InterruptedException {
        // Arrange
        CouponEntity coupon = couponJpaRepository.save(
            new CouponEntity("10% 할인 쿠폰", CouponType.RATE, BigDecimal.TEN, null, ZonedDateTime.now().plusDays(30))
        );
        IssuedCouponEntity issuedCoupon = issuedCouponJpaRepository.save(new IssuedCouponEntity(coupon.getId(), 1L, ZonedDateTime.now().plusDays(30)));

        int threadCount = 30;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    couponService.use(issuedCoupon.getId(), 1L, BigDecimal.valueOf(10000));
                    successCount.incrementAndGet();
                } catch (CoreException e) {
                    if (e.getErrorType() == ErrorType.CONFLICT) {
                        conflictCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        // Assert
        IssuedCouponEntity result = issuedCouponJpaRepository.findById(issuedCoupon.getId()).orElseThrow();
        assertAll(
            () -> assertThat(successCount.get()).isEqualTo(1),
            () -> assertThat(conflictCount.get()).isEqualTo(threadCount - 1),
            () -> assertThat(result.getStatus()).isEqualTo(CouponStatus.USED)
        );
    }
}