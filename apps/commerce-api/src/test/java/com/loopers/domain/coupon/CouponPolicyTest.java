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

class CouponPolicyTest {

    private static final ZonedDateTime EXPIRED_AT = ZonedDateTime.parse("2099-12-31T23:59:59+09:00");

    @DisplayName("CouponPolicy 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면, 정상적으로 생성되고 각 필드가 그대로 보관된다.")
        @Test
        void createsCouponPolicy_whenAllFieldsAreValid() {
            // given
            String name = "신규가입 3천원 할인";
            CouponType type = CouponType.FIXED;
            long value = 3_000L;
            Long minOrderAmount = 10_000L;

            // when
            CouponPolicy policy = new CouponPolicy(name, type, value, minOrderAmount, EXPIRED_AT);

            // then
            assertAll(
                () -> assertThat(policy.getName()).isEqualTo(name),
                () -> assertThat(policy.getType()).isEqualTo(type),
                () -> assertThat(policy.getValue()).isEqualTo(value),
                () -> assertThat(policy.getMinOrderAmount()).isEqualTo(minOrderAmount),
                () -> assertThat(policy.getExpiredAt()).isEqualTo(EXPIRED_AT)
            );
        }

        @DisplayName("최소 주문 금액이 null 이면(제약 없음), 정상적으로 생성된다.")
        @Test
        void createsCouponPolicy_whenMinOrderAmountIsNull() {
            // given
            Long minOrderAmount = null;

            // when
            CouponPolicy policy = new CouponPolicy("무제한 10% 할인", CouponType.RATE, 10L, minOrderAmount, EXPIRED_AT);

            // then
            assertThat(policy.getMinOrderAmount()).isNull();
        }

        @DisplayName("이름이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsNull() {
            // given
            String name = null;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponPolicy(name, CouponType.FIXED, 3_000L, 10_000L, EXPIRED_AT));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("쿠폰 정책 이름은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("이름이 공백 문자로만 이루어져 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // given
            String name = "   ";

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponPolicy(name, CouponType.FIXED, 3_000L, 10_000L, EXPIRED_AT));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("쿠폰 정책 이름은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("타입이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenTypeIsNull() {
            // given
            CouponType type = null;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponPolicy("쿠폰", type, 3_000L, 10_000L, EXPIRED_AT));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("쿠폰 타입은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("정액 할인 값이 0 이하이면, INVALID_COUPON_VALUE 예외가 발생한다.")
        @Test
        void throwsInvalidCouponValueException_whenFixedValueIsNotPositive() {
            // given
            long value = 0L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponPolicy("쿠폰", CouponType.FIXED, value, 10_000L, EXPIRED_AT));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_COUPON_VALUE),
                () -> assertThat(result.getCustomMessage()).isEqualTo("정액 할인 금액은 1 이상이어야 합니다.")
            );
        }

        @DisplayName("정률 할인 값이 100 을 초과하면, INVALID_COUPON_VALUE 예외가 발생한다.")
        @Test
        void throwsInvalidCouponValueException_whenRateValueExceedsHundred() {
            // given
            long value = 101L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponPolicy("쿠폰", CouponType.RATE, value, 10_000L, EXPIRED_AT));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_COUPON_VALUE),
                () -> assertThat(result.getCustomMessage()).isEqualTo("정률 할인율은 1 이상 100 이하여야 합니다.")
            );
        }

        @DisplayName("최소 주문 금액이 0 이면(null 아님), BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenMinOrderAmountIsZero() {
            // given
            Long minOrderAmount = 0L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponPolicy("쿠폰", CouponType.FIXED, 3_000L, minOrderAmount, EXPIRED_AT));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("최소 주문 금액은 1 이상이어야 합니다.")
            );
        }

        @DisplayName("최소 주문 금액이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenMinOrderAmountIsNegative() {
            // given
            Long minOrderAmount = -1L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponPolicy("쿠폰", CouponType.FIXED, 3_000L, minOrderAmount, EXPIRED_AT));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("최소 주문 금액은 1 이상이어야 합니다.")
            );
        }

        @DisplayName("만료일이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenExpiredAtIsNull() {
            // given
            ZonedDateTime expiredAt = null;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponPolicy("쿠폰", CouponType.FIXED, 3_000L, 10_000L, expiredAt));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("쿠폰 만료일은 비어있을 수 없습니다.")
            );
        }
    }

    @DisplayName("쿠폰 정책의 만료 여부를 판정할 때, ")
    @Nested
    class IsExpired {

        @DisplayName("현재 시각이 만료일 이전이면, 만료되지 않은 것으로 본다.")
        @Test
        void returnsFalse_whenNowIsBeforeExpiredAt() {
            // given
            ZonedDateTime expiredAt = ZonedDateTime.parse("2026-06-30T00:00:00+09:00");
            CouponPolicy policy = new CouponPolicy("쿠폰", CouponType.FIXED, 3_000L, 10_000L, expiredAt);
            ZonedDateTime now = ZonedDateTime.parse("2026-06-29T23:59:59+09:00");

            // when
            boolean expired = policy.isExpired(now);

            // then
            assertThat(expired).isFalse();
        }

        @DisplayName("현재 시각이 만료일과 정확히 같으면, 아직 만료되지 않은 것으로 본다.")
        @Test
        void returnsFalse_whenNowEqualsExpiredAt() {
            // given
            ZonedDateTime expiredAt = ZonedDateTime.parse("2026-06-30T00:00:00+09:00");
            CouponPolicy policy = new CouponPolicy("쿠폰", CouponType.FIXED, 3_000L, 10_000L, expiredAt);
            ZonedDateTime now = ZonedDateTime.parse("2026-06-30T00:00:00+09:00");

            // when
            boolean expired = policy.isExpired(now);

            // then
            assertThat(expired).isFalse();
        }

        @DisplayName("현재 시각이 만료일 이후이면, 만료된 것으로 본다.")
        @Test
        void returnsTrue_whenNowIsAfterExpiredAt() {
            // given
            ZonedDateTime expiredAt = ZonedDateTime.parse("2026-06-30T00:00:00+09:00");
            CouponPolicy policy = new CouponPolicy("쿠폰", CouponType.FIXED, 3_000L, 10_000L, expiredAt);
            ZonedDateTime now = ZonedDateTime.parse("2026-06-30T00:00:01+09:00");

            // when
            boolean expired = policy.isExpired(now);

            // then
            assertThat(expired).isTrue();
        }
    }

    @DisplayName("쿠폰 정책의 메타 정보를 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("유효한 값으로 수정하면, 이름·최소주문금액·만료일이 갱신되고 타입과 할인값은 그대로 유지된다.")
        @Test
        void updatesMetaFields_whenAllValuesAreValid() {
            // given
            CouponPolicy policy = new CouponPolicy("기존 쿠폰", CouponType.FIXED, 3_000L, 10_000L, EXPIRED_AT);
            ZonedDateTime newExpiredAt = ZonedDateTime.parse("2100-01-31T23:59:59+09:00");

            // when
            policy.update("변경된 쿠폰", 20_000L, newExpiredAt);

            // then
            assertAll(
                () -> assertThat(policy.getName()).isEqualTo("변경된 쿠폰"),
                () -> assertThat(policy.getMinOrderAmount()).isEqualTo(20_000L),
                () -> assertThat(policy.getExpiredAt()).isEqualTo(newExpiredAt),
                () -> assertThat(policy.getType()).isEqualTo(CouponType.FIXED),
                () -> assertThat(policy.getValue()).isEqualTo(3_000L)
            );
        }

        @DisplayName("최소 주문 금액을 null 로 수정하면(제약 해제), 정상적으로 갱신된다.")
        @Test
        void updatesMinOrderAmountToNull_whenNullGiven() {
            // given
            CouponPolicy policy = new CouponPolicy("기존 쿠폰", CouponType.FIXED, 3_000L, 10_000L, EXPIRED_AT);

            // when
            policy.update("기존 쿠폰", null, EXPIRED_AT);

            // then
            assertThat(policy.getMinOrderAmount()).isNull();
        }

        @DisplayName("이름이 공백이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // given
            CouponPolicy policy = new CouponPolicy("기존 쿠폰", CouponType.FIXED, 3_000L, 10_000L, EXPIRED_AT);

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> policy.update("   ", 10_000L, EXPIRED_AT));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("쿠폰 정책 이름은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("최소 주문 금액이 0 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenMinOrderAmountIsZero() {
            // given
            CouponPolicy policy = new CouponPolicy("기존 쿠폰", CouponType.FIXED, 3_000L, 10_000L, EXPIRED_AT);

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> policy.update("기존 쿠폰", 0L, EXPIRED_AT));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("최소 주문 금액은 1 이상이어야 합니다.")
            );
        }

        @DisplayName("만료일이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenExpiredAtIsNull() {
            // given
            CouponPolicy policy = new CouponPolicy("기존 쿠폰", CouponType.FIXED, 3_000L, 10_000L, EXPIRED_AT);

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> policy.update("기존 쿠폰", 10_000L, null));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("쿠폰 만료일은 비어있을 수 없습니다.")
            );
        }
    }
}
