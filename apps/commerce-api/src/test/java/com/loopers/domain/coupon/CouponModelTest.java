package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.ZonedDateTime;

import static com.loopers.fixture.CouponModelFixture.aCoupon;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CouponModelTest {

    @Nested
    @DisplayName("CouponModel 생성")
    class Create {

        @DisplayName("유효한 값으로 생성하면 활성 상태의 쿠폰 템플릿이 만들어진다")
        @Test
        void given_validInput_when_create_then_createsActiveCoupon() {
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(7);
            CouponModel coupon = new CouponModel("10% 할인", CouponType.RATE, 10L, 5000L, expiredAt);

            assertAll(
                    () -> assertThat(coupon.getName()).isEqualTo("10% 할인"),
                    () -> assertThat(coupon.getType()).isEqualTo(CouponType.RATE),
                    () -> assertThat(coupon.getValue()).isEqualTo(10L),
                    () -> assertThat(coupon.getMinOrderAmount()).isEqualTo(5000L),
                    () -> assertThat(coupon.getExpiredAt()).isEqualTo(expiredAt),
                    () -> assertThat(coupon.isActive()).isTrue()
            );
        }

        @DisplayName("최소 주문 금액은 null이어도 정상 생성된다 (조건 없음)")
        @Test
        void given_nullMinOrderAmount_when_create_then_creates() {
            CouponModel coupon = aCoupon().withMinOrderAmount(null).build();

            assertThat(coupon.getMinOrderAmount()).isNull();
        }

        @DisplayName("이름이 null이거나 공백이면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t"})
        void given_nullOrBlankName_when_create_then_throwsBadRequest(String invalidName) {
            CoreException result = assertThrows(CoreException.class,
                    () -> aCoupon().withName(invalidName).build());
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("할인 값이 0 이하이면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        void given_nonPositiveValue_when_create_then_throwsBadRequest(long value) {
            CoreException result = assertThrows(CoreException.class,
                    () -> aCoupon().withValue(value).build());
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정률 쿠폰의 할인 값이 100을 초과하면 BAD_REQUEST 예외가 발생한다")
        @Test
        void given_rateValueOver100_when_create_then_throwsBadRequest() {
            CoreException result = assertThrows(CoreException.class,
                    () -> aCoupon().withType(CouponType.RATE).withValue(101L).build());
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정액 쿠폰은 할인 값이 100을 초과해도 정상 생성된다")
        @Test
        void given_fixedValueOver100_when_create_then_creates() {
            CouponModel coupon = aCoupon().withType(CouponType.FIXED).withValue(5000L).build();
            assertThat(coupon.getValue()).isEqualTo(5000L);
        }

        @DisplayName("만료 시각이 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void given_nullExpiredAt_when_create_then_throwsBadRequest() {
            CoreException result = assertThrows(CoreException.class,
                    () -> aCoupon().withExpiredAt(null).build());
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("할인 계산 (calculateDiscount)")
    class CalculateDiscount {

        @DisplayName("정액 쿠폰은 할인 값만큼, 주문 금액을 넘지 않게 깎는다")
        @Test
        void given_fixed_when_calculate_then_min() {
            CouponModel coupon = aCoupon().withType(CouponType.FIXED).withValue(3000L).build();

            assertAll(
                    () -> assertThat(coupon.calculateDiscount(10000L)).isEqualTo(3000L),
                    () -> assertThat(coupon.calculateDiscount(2000L)).isEqualTo(2000L)
            );
        }

        @DisplayName("정률 쿠폰은 주문 금액의 비율만큼 버림으로 깎는다")
        @Test
        void given_rate_when_calculate_then_flooredPercent() {
            CouponModel coupon = aCoupon().withType(CouponType.RATE).withValue(10L).build();

            assertThat(coupon.calculateDiscount(9999L)).isEqualTo(999L);
        }
    }

    @Nested
    @DisplayName("적용 가능 검증 (ensureUsableAt)")
    class EnsureUsable {

        @DisplayName("만료되지 않았고 최소 주문 금액을 충족하면 통과한다")
        @Test
        void given_validUsable_when_ensure_then_passes() {
            CouponModel coupon = aCoupon().withMinOrderAmount(5000L)
                    .withExpiredAt(ZonedDateTime.now().plusDays(1)).build();

            assertDoesNotThrow(() -> coupon.ensureUsableAt(ZonedDateTime.now(), 10000L));
        }

        @DisplayName("만료 시각이 지났으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void given_expired_when_ensure_then_throwsBadRequest() {
            ZonedDateTime now = ZonedDateTime.now();
            CouponModel coupon = aCoupon().withExpiredAt(now.minusSeconds(1)).build();

            CoreException result = assertThrows(CoreException.class,
                    () -> coupon.ensureUsableAt(now, 10000L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("주문 금액이 최소 주문 금액 미만이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void given_belowMinOrderAmount_when_ensure_then_throwsBadRequest() {
            CouponModel coupon = aCoupon().withMinOrderAmount(10000L)
                    .withExpiredAt(ZonedDateTime.now().plusDays(1)).build();

            CoreException result = assertThrows(CoreException.class,
                    () -> coupon.ensureUsableAt(ZonedDateTime.now(), 9999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("soft delete")
    class SoftDelete {

        @DisplayName("delete() 하면 비활성이 된다")
        @Test
        void given_active_when_delete_then_inactive() {
            CouponModel coupon = aCoupon().build();
            coupon.delete();
            assertThat(coupon.isActive()).isFalse();
        }

        @DisplayName("삭제된 쿠폰을 restore() 하면 다시 활성이 된다")
        @Test
        void given_deleted_when_restore_then_active() {
            CouponModel coupon = aCoupon().build();
            coupon.delete();
            coupon.restore();
            assertThat(coupon.isActive()).isTrue();
        }
    }
}
