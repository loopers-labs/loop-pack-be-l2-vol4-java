package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTemplateTest {

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);
    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);

    @DisplayName("쿠폰 템플릿을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("정액 타입으로 생성하면 FIXED 타입으로 저장된다.")
        @Test
        void creates_fixed_type() {
            CouponTemplate template = new CouponTemplate("신규가입 1000원 할인", CouponType.FIXED, 1000L, null, FUTURE);

            assertThat(template.getType()).isEqualTo(CouponType.FIXED);
        }

        @DisplayName("정률 타입으로 생성하면 RATE 타입으로 저장된다.")
        @Test
        void creates_rate_type() {
            CouponTemplate template = new CouponTemplate("신규가입 10% 할인", CouponType.RATE, 10L, null, FUTURE);

            assertThat(template.getType()).isEqualTo(CouponType.RATE);
        }

        @DisplayName("이름이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_name_is_blank() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new CouponTemplate("  ", CouponType.FIXED, 1000L, null, FUTURE));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("할인 값이 0 이하이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_value_is_zero_or_negative() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new CouponTemplate("할인", CouponType.FIXED, 0L, null, FUTURE));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정률 타입에서 할인율이 100을 초과하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_rate_exceeds_100() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new CouponTemplate("할인", CouponType.RATE, 101L, null, FUTURE));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료일이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_expired_at_is_null() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new CouponTemplate("할인", CouponType.FIXED, 1000L, null, null));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰 템플릿을 수정할 때,")
    @Nested
    class Update {

        @DisplayName("유효한 값으로 수정하면 필드가 변경된다.")
        @Test
        void updates_fields_successfully() {
            CouponTemplate template = new CouponTemplate("기존 쿠폰", CouponType.FIXED, 1000L, null, FUTURE);

            template.update("수정 쿠폰", CouponType.RATE, 10L, 5000L, FUTURE);

            assertThat(template.getName()).isEqualTo("수정 쿠폰");
            assertThat(template.getType()).isEqualTo(CouponType.RATE);
            assertThat(template.getValue()).isEqualTo(10L);
            assertThat(template.getMinOrderAmount()).isEqualTo(5000L);
        }

        @DisplayName("이름이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_name_is_blank() {
            CouponTemplate template = new CouponTemplate("기존 쿠폰", CouponType.FIXED, 1000L, null, FUTURE);

            CoreException ex = assertThrows(CoreException.class,
                () -> template.update("  ", CouponType.FIXED, 1000L, null, FUTURE));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정률 타입에서 할인율이 100을 초과하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_rate_exceeds_100_on_update() {
            CouponTemplate template = new CouponTemplate("기존 쿠폰", CouponType.FIXED, 1000L, null, FUTURE);

            CoreException ex = assertThrows(CoreException.class,
                () -> template.update("쿠폰", CouponType.RATE, 101L, null, FUTURE));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("만료 여부를 확인할 때,")
    @Nested
    class IsExpired {

        @DisplayName("만료일이 현재보다 과거이면 true를 반환한다.")
        @Test
        void returns_true_when_expired() {
            CouponTemplate template = new CouponTemplate("할인", CouponType.FIXED, 1000L, null, PAST);

            assertThat(template.isExpired()).isTrue();
        }

        @DisplayName("만료일이 현재보다 미래이면 false를 반환한다.")
        @Test
        void returns_false_when_not_expired() {
            CouponTemplate template = new CouponTemplate("할인", CouponType.FIXED, 1000L, null, FUTURE);

            assertThat(template.isExpired()).isFalse();
        }
    }

    @DisplayName("할인 금액을 계산할 때,")
    @Nested
    class CalculateDiscount {

        @DisplayName("정액 쿠폰은 value만큼 할인한다.")
        @Test
        void fixed_coupon_discounts_by_value() {
            CouponTemplate template = new CouponTemplate("할인", CouponType.FIXED, 3000L, null, FUTURE);

            assertThat(template.calculateDiscount(20000L)).isEqualTo(3000L);
        }

        @DisplayName("정액 쿠폰은 주문금액보다 할인액이 크면 주문금액을 반환한다.")
        @Test
        void fixed_coupon_discount_capped_at_order_amount() {
            CouponTemplate template = new CouponTemplate("할인", CouponType.FIXED, 5000L, null, FUTURE);

            assertThat(template.calculateDiscount(3000L)).isEqualTo(3000L);
        }

        @DisplayName("정률 쿠폰은 주문금액의 퍼센트만큼 할인한다.")
        @Test
        void rate_coupon_discounts_by_percentage() {
            CouponTemplate template = new CouponTemplate("할인", CouponType.RATE, 10L, null, FUTURE);

            assertThat(template.calculateDiscount(20000L)).isEqualTo(2000L);
        }

        @DisplayName("최소 주문 금액 미만이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_order_amount_is_below_minimum() {
            CouponTemplate template = new CouponTemplate("할인", CouponType.FIXED, 1000L, 10000L, FUTURE);

            CoreException ex = assertThrows(CoreException.class,
                () -> template.calculateDiscount(9000L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
