package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTemplateTest {

    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2099, 12, 31, 23, 59);

    @DisplayName("정액(FIXED) 쿠폰의 할인액은 ")
    @Nested
    class FixedDiscount {
        @DisplayName("주문 금액이 쿠폰 value 보다 크면, value 그대로다.")
        @Test
        void normalCase() {
            // arrange
            CouponTemplate t = new CouponTemplate("정액1000", CouponType.FIXED, 1000L, 0L, FAR_FUTURE);

            // act
            long discount = t.discountFor(5000L);

            // assert
            assertThat(discount).isEqualTo(1000L);
        }

        @DisplayName("주문 금액이 쿠폰 value 보다 작으면, 주문 금액까지만 할인된다.")
        @Test
        void cappedByOrderAmount() {
            // arrange
            CouponTemplate t = new CouponTemplate("정액1000", CouponType.FIXED, 1000L, 0L, FAR_FUTURE);

            // act
            long discount = t.discountFor(500L);

            // assert
            assertThat(discount).isEqualTo(500L);
        }
    }

    @DisplayName("정률(RATE) 쿠폰의 할인액은 주문 금액 × value / 100 이다.")
    @Test
    void rateDiscount() {
        // arrange
        CouponTemplate t = new CouponTemplate("10%", CouponType.RATE, 10L, 0L, FAR_FUTURE);

        // act
        long discount = t.discountFor(10_000L);

        // assert
        assertThat(discount).isEqualTo(1000L);
    }

    @DisplayName("최소 주문 금액 미달이면, 할인액은 0 이다.")
    @Test
    void zeroWhenBelowMin() {
        // arrange
        CouponTemplate t = new CouponTemplate("10%", CouponType.RATE, 10L, 10_000L, FAR_FUTURE);

        // act
        long discount = t.discountFor(5_000L);

        // assert
        assertThat(discount).isZero();
    }

    @DisplayName("정률 value 가 100 을 초과하면, 생성자에서 BAD_REQUEST 예외가 발생한다.")
    @Test
    void throwsBadRequest_whenRateExceedsHundred() {
        // act
        CoreException result = assertThrows(CoreException.class, () ->
            new CouponTemplate("이상한쿠폰", CouponType.RATE, 150L, 0L, FAR_FUTURE)
        );

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("만료 시각 이후 호출하면, 만료 상태로 판정된다.")
    @Test
    void expiredAfter() {
        // arrange
        LocalDateTime past = LocalDateTime.of(2020, 1, 1, 0, 0);
        CouponTemplate t = new CouponTemplate("만료됨", CouponType.FIXED, 1000L, 0L, past);

        // act
        boolean expired = t.isExpiredAt(LocalDateTime.now());

        // assert
        assertThat(expired).isTrue();
    }
}
