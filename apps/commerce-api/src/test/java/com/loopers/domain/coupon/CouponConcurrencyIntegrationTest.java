package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 쿠폰 사용의 낙관적 락(@Version) 동작을 검증한다.
 * 같은 IssuedCoupon 한 장을 여러 스레드가 동시에 use 하면, 커밋 시점의 버전 비교로
 * 단 1건만 성공하고 나머지는 실패해야 한다. @Version이 없다면 모든 트랜잭션이 자기 스냅샷에서
 * AVAILABLE을 보고 UPDATE 하여 lost update(전부 성공)가 발생한다.
 */
@SpringBootTest
class CouponConcurrencyIntegrationTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일한 쿠폰 한 장을 여러 스레드가 동시에 사용하면 1건만 성공하고 쿠폰은 한 번만 USED 된다")
    @Test
    void onlyOneSucceeds_whenSameCouponUsedConcurrently() throws InterruptedException {
        // given
        Long userId = 1L;
        CouponTemplate template = couponTemplateRepository.save(
            new CouponTemplate("동시성 쿠폰", CouponType.FIXED, 1_000L, null, ZonedDateTime.now().plusDays(7)));
        Long couponId = issuedCouponRepository.save(new IssuedCoupon(userId, template.getId())).getId();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        // when - 10개 스레드가 같은 쿠폰을 동시에 사용 시도
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    couponService.use(userId, couponId, Money.of(10_000L));
                    success.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ObjectOptimisticLockingFailureException | CoreException e) {
                    failure.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startGate.countDown();
        assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // then - 단 1건만 성공, 나머지는 실패, 쿠폰은 한 번만 USED
        assertAll(
            () -> assertThat(success.get()).isEqualTo(1),
            () -> assertThat(failure.get()).isEqualTo(threadCount - 1),
            () -> assertThat(issuedCouponRepository.findById(couponId).orElseThrow().getStatus())
                .isEqualTo(CouponStatus.USED)
        );
    }
}
