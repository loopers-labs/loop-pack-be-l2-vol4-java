package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTest {

    @DisplayName("쿠폰 템플릿을 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("모든 값이 유효하면, 정상적으로 생성된다.")
        @Test
        void createsCoupon_whenAllFieldsAreValid() {
            // arrange
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(7);

            // act
            Coupon coupon = new Coupon("신규가입 10% 할인", CouponType.RATE, 10L, 10_000L, expiredAt);

            // assert
            assertAll(
                () -> assertThat(coupon.getName()).isEqualTo("신규가입 10% 할인"),
                () -> assertThat(coupon.getType()).isEqualTo(CouponType.RATE),
                () -> assertThat(coupon.getValue()).isEqualTo(10L),
                () -> assertThat(coupon.getMinOrderAmount()).isEqualTo(10_000L),
                () -> assertThat(coupon.getExpiredAt()).isEqualTo(expiredAt),
                () -> assertThat(coupon.isDeleted()).isFalse()
            );
        }

        @DisplayName("쿠폰명이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Coupon(" ", CouponType.FIXED, 1_000L, 0L, ZonedDateTime.now().plusDays(7));
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정률 쿠폰 값이 100을 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenRateValueIsGreaterThan100() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Coupon("과한 할인", CouponType.RATE, 101L, 0L, ZonedDateTime.now().plusDays(7));
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰을 발급할 때, ")
    @Nested
    class Issue {
        @DisplayName("회원 로그인 ID가 유효하면, 사용 가능한 발급 쿠폰을 생성한다.")
        @Test
        void issuesCoupon_whenUserLoginIdIsValid() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            ZonedDateTime expiredAt = now.plusDays(7);
            Coupon coupon = Coupon.reconstruct(1L, "신규가입 10% 할인", CouponType.RATE, 10L, 10_000L, expiredAt, false);

            // act
            IssuedCoupon issuedCoupon = coupon.issueTo("user1234", now);

            // assert
            assertAll(
                () -> assertThat(issuedCoupon.getCouponId()).isEqualTo(1L),
                () -> assertThat(issuedCoupon.getUserLoginId()).isEqualTo("user1234"),
                () -> assertThat(issuedCoupon.currentStatus(now)).isEqualTo(CouponStatus.AVAILABLE),
                () -> assertThat(issuedCoupon.getExpiredAt()).isEqualTo(expiredAt)
            );
        }

        @DisplayName("현재 시각이 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNowIsNull() {
            // arrange
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(7);
            Coupon coupon = Coupon.reconstruct(1L, "신규가입 10% 할인", CouponType.RATE, 10L, 10_000L, expiredAt, false);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                coupon.issueTo("user1234", null);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료된 쿠폰이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflictException_whenCouponIsExpired() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            Coupon coupon = Coupon.reconstruct(1L, "신규가입 10% 할인", CouponType.RATE, 10L, 10_000L, now, false);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                coupon.issueTo("user1234", now);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("할인 금액을 계산할 때, ")
    @Nested
    class CalculateDiscount {
        @DisplayName("정액 쿠폰이면, 쿠폰 금액만큼 할인한다.")
        @Test
        void calculatesFixedDiscount() {
            // arrange
            Coupon coupon = new Coupon("1천원 할인", CouponType.FIXED, 1_000L, 0L, ZonedDateTime.now().plusDays(7));

            // act
            Long discount = coupon.calculateDiscount(10_000L);

            // assert
            assertThat(discount).isEqualTo(1_000L);
        }

        @DisplayName("정액 쿠폰 금액이 주문 금액보다 크면, 주문 금액까지만 할인한다.")
        @Test
        void limitsFixedDiscountToOrderAmount() {
            // arrange
            Coupon coupon = new Coupon("1만원 할인", CouponType.FIXED, 10_000L, 0L, ZonedDateTime.now().plusDays(7));

            // act
            Long discount = coupon.calculateDiscount(7_000L);

            // assert
            assertThat(discount).isEqualTo(7_000L);
        }

        @DisplayName("정률 쿠폰이면, 주문 금액에 비율을 적용해 할인한다.")
        @Test
        void calculatesRateDiscount() {
            // arrange
            Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10L, 0L, ZonedDateTime.now().plusDays(7));

            // act
            Long discount = coupon.calculateDiscount(12_000L);

            // assert
            assertThat(discount).isEqualTo(1_200L);
        }

        @DisplayName("최소 주문 금액을 만족하지 못하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflictException_whenOrderAmountIsLessThanMinOrderAmount() {
            // arrange
            Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10L, 10_000L, ZonedDateTime.now().plusDays(7));

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                coupon.calculateDiscount(9_000L);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("주문 금액이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenOrderAmountIsNegative() {
            // arrange
            Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10L, 0L, ZonedDateTime.now().plusDays(7));

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                coupon.calculateDiscount(-1L);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
