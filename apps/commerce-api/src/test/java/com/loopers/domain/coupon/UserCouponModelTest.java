package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserCouponModelTest {

    @DisplayName("쿠폰을 발급할 때,")
    @Nested
    class Issue {

        private final ZonedDateTime now = ZonedDateTime.now();
        private final ZonedDateTime expiredAt = now.plusDays(7);

        private CouponModel coupon() {
            return coupon(DiscountType.FIXED, 5_000);
        }

        private CouponModel coupon(DiscountType type, int value) {
            CouponModel coupon = CouponModel.builder()
                .rawName("신규 가입 쿠폰")
                .type(type)
                .rawValue(value)
                .rawMinOrderAmount(10_000)
                .rawExpiredAt(expiredAt)
                .now(now)
                .build();
            ReflectionTestUtils.setField(coupon, "id", 1L);

            return coupon;
        }

        @DisplayName("템플릿 정보를 스냅샷으로 복사한 발급 쿠폰이 생성된다.")
        @Test
        void copiesTemplateSnapshot() {
            // arrange
            CouponModel coupon = coupon();

            // act
            UserCouponModel userCoupon = UserCouponModel.issue(100L, coupon);

            // assert
            assertAll(
                () -> assertThat(userCoupon.getUserId()).isEqualTo(100L),
                () -> assertThat(userCoupon.getCouponId()).isEqualTo(1L),
                () -> assertThat(userCoupon.getName()).isEqualTo("신규 가입 쿠폰"),
                () -> assertThat(userCoupon.getDiscountType()).isEqualTo(DiscountType.FIXED),
                () -> assertThat(userCoupon.getDiscountValue()).isEqualTo(5_000),
                () -> assertThat(userCoupon.getMinOrderAmount()).isEqualTo(10_000),
                () -> assertThat(userCoupon.getExpiredAt()).isEqualTo(expiredAt)
            );
        }

        @DisplayName("정률 템플릿에서 발급하면 할인 타입·할인 값이 그대로 스냅샷된다.")
        @Test
        void copiesRateSnapshot() {
            // arrange
            CouponModel coupon = coupon(DiscountType.RATE, 20);

            // act
            UserCouponModel userCoupon = UserCouponModel.issue(100L, coupon);

            // assert
            assertAll(
                () -> assertThat(userCoupon.getDiscountType()).isEqualTo(DiscountType.RATE),
                () -> assertThat(userCoupon.getDiscountValue()).isEqualTo(20)
            );
        }

        @DisplayName("발급 직후에는 사용 시각이 없다.")
        @Test
        void hasNoUsedAt_whenJustIssued() {
            // arrange
            CouponModel coupon = coupon();

            // act
            UserCouponModel userCoupon = UserCouponModel.issue(100L, coupon);

            // assert
            assertThat(userCoupon.getUsedAt()).isNull();
        }
    }
}
