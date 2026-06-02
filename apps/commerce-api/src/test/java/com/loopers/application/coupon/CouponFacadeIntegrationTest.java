package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.policy.FixedCouponDiscountPolicy;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class CouponFacadeIntegrationTest {

    private static final String COUPON_NAME = "1주년 쿠폰";
    private static final ZonedDateTime EXPIRED_AT = ZonedDateTime.parse("2026-12-31T23:59:59+09:00");
    private static final FixedCouponDiscountPolicy FIXED_POLICY = new FixedCouponDiscountPolicy();

    private final CouponFacade couponFacade;
    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponFacadeIntegrationTest(
        CouponFacade couponFacade,
        CouponTemplateRepository couponTemplateRepository,
        UserCouponJpaRepository userCouponJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.couponFacade = couponFacade;
        this.couponTemplateRepository = couponTemplateRepository;
        this.userCouponJpaRepository = userCouponJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("쿠폰을 발급할 때 ")
    @Nested
    class IssueCoupon {

        @DisplayName("같은 사용자가 같은 쿠폰을 동시에 발급 요청하면, 하나의 쿠폰만 발급하고 나머지는 실패한다.")
        @Test
        void issuesOnlyOneCouponAndRejectsDuplicate_whenSameUserRequestsSameCouponConcurrently() throws Exception {
            // arrange
            Long userId = 1L;
            CouponTemplate couponTemplate = createCouponTemplate();
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);

            try {
                Future<CouponIssueAttempt> first = executor.submit(() -> issueCouponAfter(start, userId, couponTemplate.getId()));
                Future<CouponIssueAttempt> second = executor.submit(() -> issueCouponAfter(start, userId, couponTemplate.getId()));

                // act
                start.countDown();
                List<CouponIssueAttempt> attempts = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
                );

                // assert
                assertAll(
                    () -> assertThat(attempts).filteredOn(CouponIssueAttempt::succeeded).hasSize(1),
                    () -> assertThat(attempts)
                        .filteredOn(attempt -> !attempt.succeeded())
                        .extracting(CouponIssueAttempt::errorType)
                        .containsExactly(ErrorType.CONFLICT),
                    () -> assertThat(userCouponJpaRepository.count()).isEqualTo(1)
                );
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private CouponTemplate createCouponTemplate() {
        return couponTemplateRepository.save(CouponTemplate.create(
            COUPON_NAME,
            CouponType.FIXED,
            2_000L,
            10_000L,
            EXPIRED_AT,
            FIXED_POLICY
        ));
    }

    private CouponIssueAttempt issueCouponAfter(CountDownLatch start, Long userId, Long couponTemplateId) {
        try {
            start.await();
            couponFacade.issueCoupon(new IssueCouponCommand(userId, couponTemplateId));
            return CouponIssueAttempt.success();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("쿠폰 발급 시작 대기 중 인터럽트가 발생했습니다.", e);
        } catch (CoreException e) {
            return CouponIssueAttempt.failure(e.getErrorType());
        }
    }

    private record CouponIssueAttempt(boolean succeeded, ErrorType errorType) {

        private static CouponIssueAttempt success() {
            return new CouponIssueAttempt(true, null);
        }

        private static CouponIssueAttempt failure(ErrorType errorType) {
            return new CouponIssueAttempt(false, errorType);
        }
    }
}
