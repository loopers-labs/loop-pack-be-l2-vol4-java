package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserCouponServiceTest {

    private FakeCouponTemplateRepository fakeTemplateRepository;
    private FakeUserCouponRepository fakeUserCouponRepository;
    private CouponTemplateService couponTemplateService;
    private UserCouponService userCouponService;

    @BeforeEach
    void setUp() {
        fakeTemplateRepository = new FakeCouponTemplateRepository();
        fakeUserCouponRepository = new FakeUserCouponRepository();
        couponTemplateService = new CouponTemplateService(fakeTemplateRepository);
        userCouponService = new UserCouponService(fakeUserCouponRepository, couponTemplateService);
    }

    private CouponTemplate newFixedTemplate(long value, long minOrder) {
        return couponTemplateService.create("쿠폰", CouponType.FIXED, value, minOrder,
            LocalDateTime.of(2099, 12, 31, 23, 59));
    }

    @DisplayName("쿠폰 발급 시, ")
    @Nested
    class Issue {
        @DisplayName("정상 발급되면 상태는 AVAILABLE 이다.")
        @Test
        void issues() {
            // arrange
            CouponTemplate template = newFixedTemplate(1000L, 0L);

            // act
            UserCoupon issued = userCouponService.issue(100L, template.getId());

            // assert
            assertAll(
                () -> assertThat(issued.getUserId()).isEqualTo(100L),
                () -> assertThat(issued.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE)
            );
        }

        @DisplayName("존재하지 않는 템플릿이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenTemplateMissing() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userCouponService.issue(100L, 999L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("주문에 쿠폰을 사용할 때, ")
    @Nested
    class UseForOrder {
        @DisplayName("AVAILABLE 쿠폰이면 USED 로 전이되고 할인액이 반환된다.")
        @Test
        void usesAvailable() {
            // arrange
            CouponTemplate template = newFixedTemplate(1000L, 0L);
            UserCoupon issued = userCouponService.issue(100L, template.getId());

            // act
            UserCouponService.AppliedCoupon applied = userCouponService.useForOrder(100L, issued.getId(), 5000L);

            // assert
            assertAll(
                () -> assertThat(applied.discountAmount()).isEqualTo(1000L),
                () -> assertThat(fakeUserCouponRepository.find(issued.getId()).orElseThrow().getStatus())
                    .isEqualTo(UserCouponStatus.USED)
            );
        }

        @DisplayName("동일 쿠폰을 두 번 사용하려 하면, 두 번째는 CONFLICT 예외가 발생한다 — 1 회성 보장.")
        @Test
        void throwsConflict_whenAlreadyUsed() {
            // arrange
            CouponTemplate template = newFixedTemplate(1000L, 0L);
            UserCoupon issued = userCouponService.issue(100L, template.getId());
            userCouponService.useForOrder(100L, issued.getId(), 5000L);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userCouponService.useForOrder(100L, issued.getId(), 5000L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("타 유저 소유 쿠폰이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenForeignCoupon() {
            // arrange
            CouponTemplate template = newFixedTemplate(1000L, 0L);
            UserCoupon issued = userCouponService.issue(100L, template.getId());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userCouponService.useForOrder(999L, issued.getId(), 5000L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("최소 주문 금액 미달이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenBelowMin() {
            // arrange
            CouponTemplate template = newFixedTemplate(1000L, 10_000L);
            UserCoupon issued = userCouponService.issue(100L, template.getId());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userCouponService.useForOrder(100L, issued.getId(), 5_000L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("만료된 쿠폰이면 CONFLICT 예외 + EXPIRED 상태로 전이된다.")
        @Test
        void throwsConflict_whenExpired() {
            // arrange
            LocalDateTime past = LocalDateTime.of(2020, 1, 1, 0, 0);
            CouponTemplate expired = couponTemplateService.create("만료", CouponType.FIXED, 1000L, 0L, past);
            UserCoupon issued = userCouponService.issue(100L, expired.getId());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userCouponService.useForOrder(100L, issued.getId(), 5000L)
            );

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(fakeUserCouponRepository.find(issued.getId()).orElseThrow().getStatus())
                    .isEqualTo(UserCouponStatus.EXPIRED)
            );
        }
    }
}
