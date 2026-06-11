package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTemplateTest {

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(7);

    @DisplayName("쿠폰 템플릿 생성 시")
    @Nested
    class Create {

        @DisplayName("유효한 값을 입력하면 템플릿이 정상 생성된다")
        @Test
        void createsTemplate_whenAllFieldsAreValid() {
            // when
            CouponTemplate template = new CouponTemplate("10% 할인", CouponType.RATE, 10L, 10_000L, FUTURE);

            // then
            assertThat(template.getType()).isEqualTo(CouponType.RATE);
            assertThat(template.getDiscountValue()).isEqualTo(10L);
        }

        @DisplayName("최소 주문 금액이 null이어도 정상 생성된다")
        @Test
        void createsTemplate_whenMinOrderAmountIsNull() {
            // when
            CouponTemplate template = new CouponTemplate("정액 3천원", CouponType.FIXED, 3_000L, null, FUTURE);

            // then
            assertThat(template.getMinOrderAmount()).isNull();
        }

        @DisplayName("쿠폰명이 비어있으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new CouponTemplate(" ", CouponType.FIXED, 3_000L, null, FUTURE));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("할인 값이 0 이하이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenDiscountValueIsNotPositive() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new CouponTemplate("0원 할인", CouponType.FIXED, 0L, null, FUTURE));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정률 쿠폰의 할인율이 100을 초과하면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenRateExceeds100() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new CouponTemplate("과도한 할인", CouponType.RATE, 101L, null, FUTURE));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("최소 주문 금액이 음수이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenMinOrderAmountIsNegative() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new CouponTemplate("음수 최소금액", CouponType.FIXED, 1_000L, -1L, FUTURE));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료일시가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsBadRequest_whenExpiredAtIsNull() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new CouponTemplate("만료없음", CouponType.FIXED, 1_000L, null, null));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("최소 주문 금액 충족 여부 판정 시")
    @Nested
    class SatisfiesMinOrderAmount {

        @DisplayName("최소 주문 금액 조건이 없으면(null) 항상 충족한다")
        @Test
        void alwaysSatisfied_whenNoMinOrderAmount() {
            CouponTemplate template = new CouponTemplate("조건없음", CouponType.FIXED, 1_000L, null, FUTURE);
            assertThat(template.satisfiesMinOrderAmount(Money.of(1L))).isTrue();
        }

        @DisplayName("주문 금액이 최소 주문 금액 이상이면 충족한다")
        @Test
        void satisfied_whenOrderAmountIsAtLeastMin() {
            CouponTemplate template = new CouponTemplate("1만원 이상", CouponType.FIXED, 1_000L, 10_000L, FUTURE);
            assertThat(template.satisfiesMinOrderAmount(Money.of(10_000L))).isTrue();
        }

        @DisplayName("주문 금액이 최소 주문 금액 미만이면 충족하지 않는다")
        @Test
        void notSatisfied_whenOrderAmountIsBelowMin() {
            CouponTemplate template = new CouponTemplate("1만원 이상", CouponType.FIXED, 1_000L, 10_000L, FUTURE);
            assertThat(template.satisfiesMinOrderAmount(Money.of(9_999L))).isFalse();
        }
    }

    @DisplayName("만료 여부 판정 시")
    @Nested
    class IsExpired {

        @DisplayName("기준 시각이 만료일시 이후이면 만료된 것으로 판정한다")
        @Test
        void expired_whenAtIsAfterExpiredAt() {
            CouponTemplate template = new CouponTemplate("쿠폰", CouponType.FIXED, 1_000L, null, FUTURE);
            assertThat(template.isExpired(FUTURE.plusSeconds(1))).isTrue();
        }

        @DisplayName("기준 시각이 만료일시 이전이면 만료되지 않은 것으로 판정한다")
        @Test
        void notExpired_whenAtIsBeforeExpiredAt() {
            CouponTemplate template = new CouponTemplate("쿠폰", CouponType.FIXED, 1_000L, null, FUTURE);
            assertThat(template.isExpired(FUTURE.minusSeconds(1))).isFalse();
        }

        @DisplayName("기준 시각이 만료일시와 정확히 같으면 만료된 것으로 판정한다")
        @Test
        void expired_whenAtEqualsExpiredAt() {
            CouponTemplate template = new CouponTemplate("쿠폰", CouponType.FIXED, 1_000L, null, FUTURE);
            assertThat(template.isExpired(FUTURE)).isTrue();
        }
    }

    @DisplayName("할인액 계산은 타입에 위임된다")
    @Test
    void delegatesDiscountToType() {
        // given
        CouponTemplate template = new CouponTemplate("정률 10%", CouponType.RATE, 10L, null, FUTURE);

        // when
        Money discount = template.calculateDiscount(Money.of(10_000L));

        // then
        assertThat(discount).isEqualTo(Money.of(1_000L));
    }
}
