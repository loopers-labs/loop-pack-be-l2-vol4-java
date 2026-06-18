package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("domain")
class CouponModelTest {

    private static final String VALID_NAME = "신규가입 할인";
    private static final ZonedDateTime VALID_EXPIRED_AT = ZonedDateTime.now().plusDays(30);

    @DisplayName("쿠폰을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("FIXED 타입으로 올바른 정보를 입력하면 정상 생성된다.")
        @Test
        void createsCoupon_whenFixedTypeWithValidFields() {
            CouponModel coupon = new CouponModel(VALID_NAME, CouponType.FIXED, 5000, 10000, VALID_EXPIRED_AT);

            assertAll(
                () -> assertThat(coupon.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(coupon.getType()).isEqualTo(CouponType.FIXED),
                () -> assertThat(coupon.getValue()).isEqualTo(5000),
                () -> assertThat(coupon.getMinOrderAmount()).isEqualTo(10000),
                () -> assertThat(coupon.getExpiredAt()).isEqualTo(VALID_EXPIRED_AT)
            );
        }

        @DisplayName("RATE 타입으로 올바른 정보를 입력하면 정상 생성된다.")
        @Test
        void createsCoupon_whenRateTypeWithValidFields() {
            CouponModel coupon = new CouponModel(VALID_NAME, CouponType.RATE, 10, 0, VALID_EXPIRED_AT);

            assertAll(
                () -> assertThat(coupon.getType()).isEqualTo(CouponType.RATE),
                () -> assertThat(coupon.getValue()).isEqualTo(10)
            );
        }

        @DisplayName("이름이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponModel(null, CouponType.FIXED, 5000, 0, VALID_EXPIRED_AT));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 공백이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponModel("   ", CouponType.FIXED, 5000, 0, VALID_EXPIRED_AT));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("타입이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTypeIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponModel(VALID_NAME, null, 5000, 0, VALID_EXPIRED_AT));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료일이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpiredAtIsNull() {
            CoreException result = assertThrows(CoreException.class,
                () -> new CouponModel(VALID_NAME, CouponType.FIXED, 5000, 0, null));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("RATE 타입의 할인율 경계값을 검증한다.")
        @Nested
        class RateValueBoundary {

            @DisplayName("할인율이 1이면 정상 생성된다.")
            @Test
            void createsCoupon_whenRateValueIsOne() {
                CouponModel coupon = new CouponModel(VALID_NAME, CouponType.RATE, 1, 0, VALID_EXPIRED_AT);

                assertThat(coupon.getValue()).isEqualTo(1);
            }

            @DisplayName("할인율이 100이면 정상 생성된다.")
            @Test
            void createsCoupon_whenRateValueIsHundred() {
                CouponModel coupon = new CouponModel(VALID_NAME, CouponType.RATE, 100, 0, VALID_EXPIRED_AT);

                assertThat(coupon.getValue()).isEqualTo(100);
            }

            @DisplayName("할인율이 0이면 BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenRateValueIsZero() {
                CoreException result = assertThrows(CoreException.class,
                    () -> new CouponModel(VALID_NAME, CouponType.RATE, 0, 0, VALID_EXPIRED_AT));

                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }

            @DisplayName("할인율이 101이면 BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenRateValueIsOverHundred() {
                CoreException result = assertThrows(CoreException.class,
                    () -> new CouponModel(VALID_NAME, CouponType.RATE, 101, 0, VALID_EXPIRED_AT));

                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }
        }

        @DisplayName("FIXED 타입의 할인 금액 경계값을 검증한다.")
        @Nested
        class FixedValueBoundary {

            @DisplayName("할인 금액이 1이면 정상 생성된다.")
            @Test
            void createsCoupon_whenFixedValueIsOne() {
                CouponModel coupon = new CouponModel(VALID_NAME, CouponType.FIXED, 1, 0, VALID_EXPIRED_AT);

                assertThat(coupon.getValue()).isEqualTo(1);
            }

            @DisplayName("할인 금액이 0이면 BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenFixedValueIsZero() {
                CoreException result = assertThrows(CoreException.class,
                    () -> new CouponModel(VALID_NAME, CouponType.FIXED, 0, 0, VALID_EXPIRED_AT));

                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }
        }

        @DisplayName("최소 주문 금액 경계값을 검증한다.")
        @Nested
        class MinOrderAmountBoundary {

            @DisplayName("최소 주문 금액이 0이면 정상 생성된다.")
            @Test
            void createsCoupon_whenMinOrderAmountIsZero() {
                CouponModel coupon = new CouponModel(VALID_NAME, CouponType.FIXED, 5000, 0, VALID_EXPIRED_AT);

                assertThat(coupon.getMinOrderAmount()).isEqualTo(0);
            }

            @DisplayName("최소 주문 금액이 -1이면 BAD_REQUEST 예외가 발생한다.")
            @Test
            void throwsBadRequest_whenMinOrderAmountIsNegative() {
                CoreException result = assertThrows(CoreException.class,
                    () -> new CouponModel(VALID_NAME, CouponType.FIXED, 5000, -1, VALID_EXPIRED_AT));

                assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            }
        }
    }

    @DisplayName("할인 금액을 계산할 때, ")
    @Nested
    class CalculateDiscount {

        @DisplayName("FIXED 타입은 주문 금액과 무관하게 고정 금액을 반환한다.")
        @Test
        void returnsFixedValue_whenTypeIsFixed() {
            CouponModel coupon = new CouponModel(VALID_NAME, CouponType.FIXED, 3000, 10000, VALID_EXPIRED_AT);

            assertThat(coupon.calculateDiscount(20000)).isEqualTo(3000);
        }

        @DisplayName("RATE 타입은 주문 금액의 비율만큼 할인 금액을 반환한다.")
        @Test
        void returnsRateDiscount_whenTypeIsRate() {
            CouponModel coupon = new CouponModel(VALID_NAME, CouponType.RATE, 10, 0, VALID_EXPIRED_AT);

            assertThat(coupon.calculateDiscount(50000)).isEqualTo(5000);
        }

        @DisplayName("주문 금액이 최소 주문 금액과 같으면 할인이 적용된다.")
        @Test
        void appliesDiscount_whenOrderAmountEqualsMinOrderAmount() {
            CouponModel coupon = new CouponModel(VALID_NAME, CouponType.FIXED, 3000, 10000, VALID_EXPIRED_AT);

            assertThat(coupon.calculateDiscount(10000)).isEqualTo(3000);
        }

        @DisplayName("주문 금액이 최소 주문 금액보다 1 적으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderAmountIsOneBelowMinOrderAmount() {
            CouponModel coupon = new CouponModel(VALID_NAME, CouponType.FIXED, 3000, 10000, VALID_EXPIRED_AT);

            CoreException result = assertThrows(CoreException.class,
                () -> coupon.calculateDiscount(9999));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("최소 주문 금액이 0이면 주문 금액에 관계없이 할인이 적용된다.")
        @Test
        void appliesDiscount_whenMinOrderAmountIsZero() {
            CouponModel coupon = new CouponModel(VALID_NAME, CouponType.FIXED, 3000, 0, VALID_EXPIRED_AT);

            assertThat(coupon.calculateDiscount(1)).isEqualTo(3000);
        }
    }
}
