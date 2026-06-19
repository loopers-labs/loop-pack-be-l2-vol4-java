package com.loopers.domain.coupon;

import com.loopers.domain.vo.Money;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponSnapshotTest {

    @DisplayName("할인 계산 시, ")
    @Nested
    class CalculateDiscount {

        @DisplayName("정액 쿠폰은 할인 금액을 그대로 반환한다.")
        @Test
        void fixed_returnsValue() {
            // arrange
            CouponSnapshot snapshot = new CouponSnapshot("5천원 할인", CouponType.FIXED, 5000L, null);

            // act & assert
            assertThat(snapshot.calculateDiscount(Money.of(20000L))).isEqualTo(Money.of(5000L));
        }

        @DisplayName("정액 할인이 주문 금액보다 크면, 주문 금액까지만 할인한다.")
        @Test
        void fixed_cappedByOrderAmount() {
            // arrange
            CouponSnapshot snapshot = new CouponSnapshot("5천원 할인", CouponType.FIXED, 5000L, null);

            // act & assert
            assertThat(snapshot.calculateDiscount(Money.of(3000L))).isEqualTo(Money.of(3000L));
        }

        @DisplayName("정률 쿠폰은 주문 금액의 비율을 원 단위 절사로 할인한다.")
        @Test
        void rate_truncates() {
            // arrange
            CouponSnapshot snapshot = new CouponSnapshot("10% 할인", CouponType.RATE, 10L, null);

            // act & assert
            assertThat(snapshot.calculateDiscount(Money.of(999L))).isEqualTo(Money.of(99L));
        }

        @DisplayName("최소 주문 금액 미달이면 예외가 발생한다.")
        @Test
        void throws_whenBelowMinOrderAmount() {
            // arrange
            CouponSnapshot snapshot = new CouponSnapshot("10% 할인", CouponType.RATE, 10L, 10000L);

            // act & assert
            assertThrows(CoreException.class, () -> snapshot.calculateDiscount(Money.of(9999L)));
        }

        @DisplayName("최소 주문 금액과 같으면 정상 할인된다. (경계값)")
        @Test
        void calculates_whenEqualsMinOrderAmount() {
            // arrange
            CouponSnapshot snapshot = new CouponSnapshot("10% 할인", CouponType.RATE, 10L, 10000L);

            // act & assert
            assertThat(snapshot.calculateDiscount(Money.of(10000L))).isEqualTo(Money.of(1000L));
        }
    }

    @DisplayName("생성 시, ")
    @Nested
    class Create {

        @DisplayName("정률 할인율이 1~100 범위를 벗어나면 예외가 발생한다.")
        @Test
        void throws_whenRateOutOfRange() {
            // act & assert
            assertThrows(CoreException.class, () -> new CouponSnapshot("x", CouponType.RATE, 0L, null));
            assertThrows(CoreException.class, () -> new CouponSnapshot("x", CouponType.RATE, 101L, null));
        }

        @DisplayName("정액 할인 금액이 0 이하면 예외가 발생한다.")
        @Test
        void throws_whenFixedValueNotPositive() {
            // act & assert
            assertThrows(CoreException.class, () -> new CouponSnapshot("x", CouponType.FIXED, 0L, null));
        }

        @DisplayName("이름이 비어있으면 예외가 발생한다.")
        @Test
        void throws_whenNameBlank() {
            // act & assert
            assertThrows(CoreException.class, () -> new CouponSnapshot(" ", CouponType.FIXED, 1000L, null));
        }

        @DisplayName("최소 주문 금액이 0 이하면 예외가 발생한다.")
        @Test
        void throws_whenMinOrderAmountNotPositive() {
            // act & assert
            assertThrows(CoreException.class, () -> new CouponSnapshot("x", CouponType.FIXED, 1000L, 0L));
        }
    }
}
