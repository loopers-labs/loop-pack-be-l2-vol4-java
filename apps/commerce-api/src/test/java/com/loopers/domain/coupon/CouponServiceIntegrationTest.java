package com.loopers.domain.coupon;

import com.loopers.domain.coupon.policy.FixedCouponDiscountPolicy;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class CouponServiceIntegrationTest {

    private static final String COUPON_NAME = "1주년 2,000원 할인";
    private static final ZonedDateTime EXPIRED_AT = ZonedDateTime.parse("2026-12-31T23:59:59+09:00");
    private static final ZonedDateTime USED_AT = ZonedDateTime.parse("2026-06-01T12:00:00+09:00");
    private static final FixedCouponDiscountPolicy FIXED_POLICY = new FixedCouponDiscountPolicy();

    private final CouponService couponService;
    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponServiceIntegrationTest(
        CouponService couponService,
        CouponTemplateRepository couponTemplateRepository,
        UserCouponRepository userCouponRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.couponService = couponService;
        this.couponTemplateRepository = couponTemplateRepository;
        this.userCouponRepository = userCouponRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("쿠폰을 동시에 사용할 때 ")
    @Nested
    class Use {

        @DisplayName("같은 쿠폰으로 여러 요청이 동시에 사용되면, 단 한 건만 성공하고 나머지는 실패한다.")
        @Test
        void usesCouponOnlyOnce_whenConcurrentRequestsUseSameCoupon() throws InterruptedException {
            // arrange
            Long userId = 101L;
            int concurrentRequests = 10;
            CouponTemplate template = couponTemplateRepository.save(CouponTemplate.create(
                COUPON_NAME,
                CouponType.FIXED,
                2_000L,
                10_000L,
                EXPIRED_AT,
                FIXED_POLICY
            ));
            UserCoupon userCoupon = userCouponRepository.save(UserCoupon.issue(userId, template.getId(), template));
            Long userCouponId = userCoupon.getId();
            ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
            CountDownLatch latch = new CountDownLatch(concurrentRequests);
            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger failureCount = new AtomicInteger();

            // act
            for (int i = 0; i < concurrentRequests; i++) {
                executor.submit(() -> {
                    try {
                        couponService.use(CouponUse.create(userId, userCouponId, 12_000L, USED_AT));
                        successCount.incrementAndGet();
                    } catch (CoreException e) {
                        failureCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // assert
            UserCoupon used = userCouponRepository.findById(userCouponId).orElseThrow();
            assertAll(
                () -> assertThat(successCount.get()).isEqualTo(1),
                () -> assertThat(failureCount.get()).isEqualTo(concurrentRequests - 1),
                () -> assertThat(used.getStatus()).isEqualTo(UserCouponStatus.USED)
            );
        }
    }
}
