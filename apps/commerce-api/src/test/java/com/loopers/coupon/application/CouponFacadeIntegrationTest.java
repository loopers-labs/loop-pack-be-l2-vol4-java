package com.loopers.coupon.application;

import com.loopers.coupon.domain.CouponIssueStatus;
import com.loopers.coupon.domain.CouponTemplate;
import com.loopers.coupon.domain.CouponTemplateRepository;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.CouponService;
import com.loopers.coupon.domain.CouponUse;
import com.loopers.coupon.domain.UserCouponStatus;
import com.loopers.coupon.domain.policy.FixedCouponDiscountPolicy;
import com.loopers.coupon.domain.vo.CouponDiscount;
import com.loopers.coupon.infrastructure.UserCouponJpaRepository;
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
    private static final ZonedDateTime USED_AT = ZonedDateTime.parse("2026-06-01T12:00:00+09:00");
    private static final FixedCouponDiscountPolicy FIXED_POLICY = new FixedCouponDiscountPolicy();

    private final CouponFacade couponFacade;
    private final CouponService couponService;
    private final UserCouponListQuery userCouponListQuery;
    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponFacadeIntegrationTest(
        CouponFacade couponFacade,
        CouponService couponService,
        UserCouponListQuery userCouponListQuery,
        CouponTemplateRepository couponTemplateRepository,
        UserCouponJpaRepository userCouponJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.couponFacade = couponFacade;
        this.couponService = couponService;
        this.userCouponListQuery = userCouponListQuery;
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

        @DisplayName("같은 사용자가 이미 발급받은 쿠폰을 다시 요청하면, 실패 대신 기존 쿠폰을 ALREADY_ISSUED로 반환한다.")
        @Test
        void returnsExistingCoupon_whenSameUserRequestsAgain() {
            // arrange
            Long userId = 1L;
            CouponTemplate couponTemplate = createCouponTemplate();
            IssuedCouponInfo firstIssued = couponFacade.issueCoupon(new IssueCouponCommand(userId, couponTemplate.getId()));

            // act
            IssuedCouponInfo secondIssued = couponFacade.issueCoupon(new IssueCouponCommand(userId, couponTemplate.getId()));

            // assert
            assertAll(
                () -> assertThat(firstIssued.status()).isEqualTo(CouponIssueStatus.ISSUED),
                () -> assertThat(secondIssued.status()).isEqualTo(CouponIssueStatus.ALREADY_ISSUED),
                () -> assertThat(secondIssued.coupon().id()).isEqualTo(firstIssued.coupon().id()),
                () -> assertThat(userCouponJpaRepository.count()).isEqualTo(1)
            );
        }

        @DisplayName("같은 사용자가 같은 쿠폰을 동시에 발급 요청해도, 한 장만 생성되고 모든 요청이 같은 쿠폰을 반환한다.")
        @Test
        void issuesOnlyOneCoupon_whenSameUserRequestsSameCouponConcurrently() throws Exception {
            // arrange
            Long userId = 1L;
            CouponTemplate couponTemplate = createCouponTemplate();
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);

            try {
                Future<IssuedCouponInfo> first = executor.submit(() -> issueCouponAfter(start, userId, couponTemplate.getId()));
                Future<IssuedCouponInfo> second = executor.submit(() -> issueCouponAfter(start, userId, couponTemplate.getId()));

                // act
                start.countDown();
                List<IssuedCouponInfo> results = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
                );

                // assert
                assertAll(
                    () -> assertThat(results.get(1).coupon().id()).isEqualTo(results.get(0).coupon().id()),
                    () -> assertThat(results)
                        .extracting(IssuedCouponInfo::status)
                        .containsExactlyInAnyOrder(CouponIssueStatus.ISSUED, CouponIssueStatus.ALREADY_ISSUED),
                    () -> assertThat(userCouponJpaRepository.count()).isEqualTo(1)
                );
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @DisplayName("쿠폰을 사용할 때")
    @Nested
    class UseCoupon {

        @DisplayName("발급 이후 쿠폰 템플릿이 삭제되더라도, 발급 당시 조건으로 할인 금액을 계산한다.")
        @Test
        void calculatesDiscountWithIssuedSnapshot_whenCouponTemplateIsDeletedAfterIssue() {
            // arrange
            Long userId = 1L;
            CouponTemplate couponTemplate = createCouponTemplate();
            IssuedCouponInfo issuedCoupon = couponFacade.issueCoupon(new IssueCouponCommand(userId, couponTemplate.getId()));
            couponTemplate.delete();
            couponTemplateRepository.save(couponTemplate);
            CouponUse couponUse = CouponUse.create(
                userId,
                issuedCoupon.coupon().id(),
                12_000L,
                USED_AT
            );

            // act
            CouponDiscount discount = couponService.use(couponUse);

            // assert
            assertThat(discount.amount().value()).isEqualTo(2_000L);
        }
    }

    @DisplayName("내 쿠폰 목록을 조회할 때 ")
    @Nested
    class GetMyCoupons {

        @DisplayName("쿠폰 만료 시각과 조회 시각이 같으면, 표시 상태를 EXPIRED로 반환한다.")
        @Test
        void returnsExpiredStatus_whenCouponExpiresAtCurrentTime() {
            // arrange
            Long userId = 1L;
            CouponTemplate couponTemplate = createCouponTemplate();
            couponFacade.issueCoupon(new IssueCouponCommand(userId, couponTemplate.getId()));

            // act
            List<UserCouponInfo> coupons = userCouponListQuery.findMyCoupons(userId, EXPIRED_AT);

            // assert
            assertThat(coupons)
                .extracting(UserCouponInfo::displayStatus)
                .containsExactly(UserCouponStatus.EXPIRED);
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

    private IssuedCouponInfo issueCouponAfter(CountDownLatch start, Long userId, Long couponTemplateId) {
        try {
            start.await();
            return couponFacade.issueCoupon(new IssueCouponCommand(userId, couponTemplateId));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("쿠폰 발급 시작 대기 중 인터럽트가 발생했습니다.", e);
        }
    }
}
