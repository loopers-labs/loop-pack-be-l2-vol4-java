package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class CouponModelTest {

    @DisplayName("쿠폰 템플릿을 생성할 때,")
    @Nested
    class Create {

        private final ZonedDateTime now = ZonedDateTime.now();
        private final ZonedDateTime futureExpiredAt = now.plusDays(7);

        @DisplayName("유효한 값이면 입력값을 그대로 보존한 쿠폰 템플릿이 생성된다.")
        @Test
        void createsCoupon_whenValuesAreValid() {
            // arrange & act
            CouponModel coupon = CouponModel.builder()
                .rawName("신규 가입 쿠폰")
                .type(DiscountType.FIXED)
                .rawValue(5_000)
                .rawMinOrderAmount(10_000)
                .rawExpiredAt(futureExpiredAt)
                .now(now)
                .build();

            // assert
            assertAll(
                () -> assertThat(coupon.getName().value()).isEqualTo("신규 가입 쿠폰"),
                () -> assertThat(coupon.getType()).isEqualTo(DiscountType.FIXED),
                () -> assertThat(coupon.getDiscountValue()).isEqualTo(5_000),
                () -> assertThat(coupon.getMinOrderAmount().value()).isEqualTo(10_000),
                () -> assertThat(coupon.getExpiredAt().value()).isEqualTo(futureExpiredAt)
            );
        }

        @DisplayName("최소 주문 금액을 지정하지 않으면 제약 없음(0)으로 생성된다.")
        @Test
        void createsCoupon_whenMinOrderAmountIsAbsent() {
            // arrange & act
            CouponModel coupon = CouponModel.builder()
                .rawName("정률 쿠폰")
                .type(DiscountType.RATE)
                .rawValue(10)
                .rawMinOrderAmount(null)
                .rawExpiredAt(futureExpiredAt)
                .now(now)
                .build();

            // assert
            assertThat(coupon.getMinOrderAmount().value()).isZero();
        }

        @DisplayName("정률 쿠폰의 할인 값이 100을 초과하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRateValueExceedsMax() {
            // arrange & act & assert
            assertThatThrownBy(() -> CouponModel.builder()
                .rawName("정률 쿠폰")
                .type(DiscountType.RATE)
                .rawValue(101)
                .rawExpiredAt(futureExpiredAt)
                .now(now)
                .build())
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료 시각이 기준 시각 이전이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpiredAtIsPast() {
            // arrange
            ZonedDateTime pastExpiredAt = now.minusDays(1);

            // act & assert
            assertThatThrownBy(() -> CouponModel.builder()
                .rawName("만료된 쿠폰")
                .type(DiscountType.FIXED)
                .rawValue(5_000)
                .rawExpiredAt(pastExpiredAt)
                .now(now)
                .build())
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰 템플릿을 수정할 때,")
    @Nested
    class Update {

        private final ZonedDateTime now = ZonedDateTime.now();
        private final ZonedDateTime futureExpiredAt = now.plusDays(7);

        private CouponModel coupon() {
            return CouponModel.builder()
                .rawName("기존 쿠폰")
                .type(DiscountType.FIXED)
                .rawValue(5_000)
                .rawMinOrderAmount(10_000)
                .rawExpiredAt(futureExpiredAt)
                .now(now)
                .build();
        }

        @DisplayName("유효한 값이면 모든 속성이 새 값으로 갱신된다.")
        @Test
        void updatesAllFields_whenValuesAreValid() {
            // arrange
            CouponModel coupon = coupon();
            ZonedDateTime newExpiredAt = now.plusDays(30);

            // act
            coupon.update("변경 쿠폰", DiscountType.RATE, 20, 50_000, newExpiredAt, now);

            // assert
            assertAll(
                () -> assertThat(coupon.getName().value()).isEqualTo("변경 쿠폰"),
                () -> assertThat(coupon.getType()).isEqualTo(DiscountType.RATE),
                () -> assertThat(coupon.getDiscountValue()).isEqualTo(20),
                () -> assertThat(coupon.getMinOrderAmount().value()).isEqualTo(50_000),
                () -> assertThat(coupon.getExpiredAt().value()).isEqualTo(newExpiredAt)
            );
        }

        @DisplayName("최소 주문 금액을 null로 수정하면 제약 없음(0)으로 비워진다.")
        @Test
        void clearsMinOrderAmount_whenUpdatedWithNull() {
            // arrange
            CouponModel coupon = coupon();

            // act
            coupon.update("변경 쿠폰", DiscountType.FIXED, 3_000, null, futureExpiredAt, now);

            // assert
            assertThat(coupon.getMinOrderAmount().value()).isZero();
        }

        @DisplayName("정률 값이 100을 초과하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRateValueExceedsMax() {
            // arrange
            CouponModel coupon = coupon();

            // act & assert
            assertThatThrownBy(() -> coupon.update("변경 쿠폰", DiscountType.RATE, 101, null, futureExpiredAt, now))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료 시각이 기준 시각 이전이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpiredAtIsPast() {
            // arrange
            CouponModel coupon = coupon();
            ZonedDateTime pastExpiredAt = now.minusDays(1);

            // act & assert
            assertThatThrownBy(() -> coupon.update("변경 쿠폰", DiscountType.FIXED, 3_000, null, pastExpiredAt, now))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰 템플릿의 만료 여부를 확인할 때,")
    @Nested
    class IsExpired {

        private final ZonedDateTime now = ZonedDateTime.now();

        private CouponModel coupon(ZonedDateTime expiredAt, ZonedDateTime issuedAt) {
            return CouponModel.builder()
                .rawName("쿠폰")
                .type(DiscountType.FIXED)
                .rawValue(5_000)
                .rawMinOrderAmount(10_000)
                .rawExpiredAt(expiredAt)
                .now(issuedAt)
                .build();
        }

        @DisplayName("만료 시각이 기준 시각 이후이면 만료되지 않은 것으로 판정한다.")
        @Test
        void returnsFalse_whenExpiredAtIsAfterNow() {
            // arrange
            CouponModel coupon = coupon(now.plusDays(7), now);

            // act & assert
            assertThat(coupon.isExpired(now)).isFalse();
        }

        @DisplayName("만료 시각이 기준 시각과 같으면 만료되지 않은 것으로 판정한다.")
        @Test
        void returnsFalse_whenExpiredAtEqualsNow() {
            // arrange
            CouponModel coupon = coupon(now, now);

            // act & assert
            assertThat(coupon.isExpired(now)).isFalse();
        }

        @DisplayName("만료 시각이 기준 시각 이전이면 만료된 것으로 판정한다.")
        @Test
        void returnsTrue_whenExpiredAtIsBeforeNow() {
            // arrange (과거 기준 시각으로 생성해, 실제로는 만료 시각이 지난 템플릿을 만든다)
            ZonedDateTime pastExpiredAt = now.minusDays(1);
            CouponModel coupon = coupon(pastExpiredAt, pastExpiredAt.minusDays(1));

            // act & assert
            assertThat(coupon.isExpired(now)).isTrue();
        }
    }
}
