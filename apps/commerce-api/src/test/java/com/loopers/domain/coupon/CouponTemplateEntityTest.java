package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class CouponTemplateEntityTest {

    private static final String VALID_NAME = "신규 가입 쿠폰";
    private static final CouponType VALID_TYPE = CouponType.FIXED;
    private static final Long VALID_VALUE = 3000L;
    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);
    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);

    @DisplayName("쿠폰 템플릿 생성")
    @Nested
    class Create {

        @DisplayName("유효한 값으로 생성하면 성공한다.")
        @Test
        void createsCouponTemplate_whenRequestIsValid() {
            // arrange & act
            CouponTemplateEntity template = new CouponTemplateEntity(VALID_NAME, CouponType.FIXED, 3000L, null, FUTURE);

            // assert
            assertAll(
                    () -> assertEquals(VALID_NAME, template.getName()),
                    () -> assertEquals(CouponType.FIXED, template.getType()),
                    () -> assertEquals(3000L, template.getValue()),
                    () -> assertNull(template.getMinOrderAmount()),
                    () -> assertEquals(FUTURE, template.getExpiredAt())
            );
        }

        @DisplayName("name이 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenNameIsNull() {
            assertThrows(CoreException.class,
                    () -> new CouponTemplateEntity(null, VALID_TYPE, VALID_VALUE, null, FUTURE));
        }

        @DisplayName("name이 빈 문자열이면 예외가 발생한다.")
        @Test
        void throwsException_whenNameIsBlank() {
            assertThrows(CoreException.class,
                    () -> new CouponTemplateEntity("  ", VALID_TYPE, VALID_VALUE, null, FUTURE));
        }

        @DisplayName("type이 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenTypeIsNull() {
            assertThrows(CoreException.class,
                    () -> new CouponTemplateEntity(VALID_NAME, null, VALID_VALUE, null, FUTURE));
        }

        @DisplayName("value가 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenValueIsNull() {
            assertThrows(CoreException.class,
                    () -> new CouponTemplateEntity(VALID_NAME, VALID_TYPE, null, null, FUTURE));
        }

        @DisplayName("FIXED 타입에서 value가 0 이하이면 예외가 발생한다.")
        @Test
        void throwsException_whenFixedValueIsNotPositive() {
            assertThrows(CoreException.class,
                    () -> new CouponTemplateEntity(VALID_NAME, CouponType.FIXED, 0L, null, FUTURE));
        }

        @DisplayName("RATE 타입에서 value가 1 미만이면 예외가 발생한다.")
        @Test
        void throwsException_whenRateValueIsLessThanOne() {
            assertThrows(CoreException.class,
                    () -> new CouponTemplateEntity(VALID_NAME, CouponType.RATE, 0L, null, FUTURE));
        }

        @DisplayName("RATE 타입에서 value가 100 초과이면 예외가 발생한다.")
        @Test
        void throwsException_whenRateValueExceedsHundred() {
            assertThrows(CoreException.class,
                    () -> new CouponTemplateEntity(VALID_NAME, CouponType.RATE, 101L, null, FUTURE));
        }

        @DisplayName("minOrderAmount가 음수이면 예외가 발생한다.")
        @Test
        void throwsException_whenMinOrderAmountIsNegative() {
            assertThrows(CoreException.class,
                    () -> new CouponTemplateEntity(VALID_NAME, VALID_TYPE, VALID_VALUE, -1L, FUTURE));
        }

        @DisplayName("expiredAt이 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenExpiredAtIsNull() {
            assertThrows(CoreException.class,
                    () -> new CouponTemplateEntity(VALID_NAME, VALID_TYPE, VALID_VALUE, null, null));
        }

        @DisplayName("expiredAt이 과거이면 예외가 발생한다.")
        @Test
        void throwsException_whenExpiredAtIsInThePast() {
            assertThrows(CoreException.class,
                    () -> new CouponTemplateEntity(VALID_NAME, VALID_TYPE, VALID_VALUE, null, PAST));
        }
    }

    @DisplayName("만료 여부 확인")
    @Nested
    class IsExpired {

        @DisplayName("만료일이 미래이면 false를 반환한다.")
        @Test
        void returnsFalse_whenExpiredAtIsInFuture() {
            // arrange
            CouponTemplateEntity template = new CouponTemplateEntity(VALID_NAME, VALID_TYPE, VALID_VALUE, null, FUTURE);

            // act & assert
            assertFalse(template.isExpired());
        }

        @DisplayName("만료일이 지났으면 true를 반환한다.")
        @Test
        void returnsTrue_whenExpiredAtIsInPast() {
            // arrange
            CouponTemplateEntity template = CouponTemplateEntity.of("1", VALID_NAME, VALID_TYPE, VALID_VALUE, null, PAST, null, null, null);

            // act & assert
            assertTrue(template.isExpired());
        }
    }

    @DisplayName("최소 주문금액 검증")
    @Nested
    class ValidateMinOrderAmount {

        @DisplayName("minOrderAmount가 null이면 검증을 통과한다.")
        @Test
        void passes_whenMinOrderAmountIsNull() {
            // arrange
            CouponTemplateEntity template = new CouponTemplateEntity(VALID_NAME, VALID_TYPE, VALID_VALUE, null, FUTURE);

            // act & assert
            assertDoesNotThrow(() -> template.validateMinimumOrderAmount(1000L));
        }

        @DisplayName("주문금액이 최소 주문금액 이상이면 검증을 통과한다.")
        @Test
        void passes_whenOrderAmountMeetsMinimum() {
            // arrange
            CouponTemplateEntity template = new CouponTemplateEntity(VALID_NAME, VALID_TYPE, VALID_VALUE, 10000L, FUTURE);

            // act & assert
            assertDoesNotThrow(() -> template.validateMinimumOrderAmount(10000L));
        }

        @DisplayName("주문금액이 최소 주문금액 미만이면 예외가 발생한다.")
        @Test
        void throwsException_whenOrderAmountIsBelowMinimum() {
            // arrange
            CouponTemplateEntity template = new CouponTemplateEntity(VALID_NAME, VALID_TYPE, VALID_VALUE, 10000L, FUTURE);

            // act & assert
            assertThrows(CoreException.class, () -> template.validateMinimumOrderAmount(9999L));
        }
    }

    @DisplayName("할인 금액 계산")
    @Nested
    class CalculateDiscount {

        @DisplayName("FIXED 타입에서 value가 주문금액보다 작으면 value를 반환한다.")
        @Test
        void returnsValue_whenFixedValueIsLessThanOrderAmount() {
            // arrange
            CouponTemplateEntity template = new CouponTemplateEntity(VALID_NAME, CouponType.FIXED, 3000L, null, FUTURE);

            // act
            Long discount = template.calculateDiscount(10000L);

            // assert
            assertEquals(3000L, discount);
        }

        @DisplayName("FIXED 타입에서 value가 주문금액보다 크면 할인액은 주문금액이 된다.")
        @Test
        void returnsOrderAmount_whenFixedValueExceedsOrderAmount() {
            // arrange
            CouponTemplateEntity template = new CouponTemplateEntity(VALID_NAME, CouponType.FIXED, 5000L, null, FUTURE);

            // act
            Long discount = template.calculateDiscount(3000L);

            // assert
            assertEquals(3000L, discount);
        }

        @DisplayName("RATE 타입에서 주문금액의 비율만큼 할인 금액을 반환한다.")
        @Test
        void returnsRateDiscount_whenTypeIsRate() {
            // arrange
            CouponTemplateEntity template = new CouponTemplateEntity(VALID_NAME, CouponType.RATE, 10L, null, FUTURE);

            // act
            Long discount = template.calculateDiscount(20000L);

            // assert
            assertEquals(2000L, discount);
        }
    }

    @DisplayName("쿠폰 템플릿 수정")
    @Nested
    class Update {

        @DisplayName("name, minOrderAmount, expiredAt을 수정할 수 있다.")
        @Test
        void updatesNameAndMinOrderAmountAndExpiredAt() {
            // arrange
            CouponTemplateEntity template = new CouponTemplateEntity(VALID_NAME, VALID_TYPE, VALID_VALUE, null, FUTURE);
            ZonedDateTime newExpiredAt = FUTURE.plusDays(10);

            // act
            template.update("변경된 쿠폰", 5000L, newExpiredAt);

            // assert
            assertAll(
                    () -> assertEquals("변경된 쿠폰", template.getName()),
                    () -> assertEquals(5000L, template.getMinOrderAmount()),
                    () -> assertEquals(newExpiredAt, template.getExpiredAt())
            );
        }

        @DisplayName("update 후에도 type과 value는 변경되지 않는다.")
        @Test
        void doesNotChangeTypeAndValue_afterUpdate() {
            // arrange
            CouponTemplateEntity template = new CouponTemplateEntity(VALID_NAME, CouponType.FIXED, 3000L, null, FUTURE);

            // act
            template.update("변경된 쿠폰", null, FUTURE.plusDays(10));

            // assert
            assertAll(
                    () -> assertEquals(CouponType.FIXED, template.getType()),
                    () -> assertEquals(3000L, template.getValue())
            );
        }

        @DisplayName("name이 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenUpdateNameIsNull() {
            // arrange
            CouponTemplateEntity template = new CouponTemplateEntity(VALID_NAME, VALID_TYPE, VALID_VALUE, null, FUTURE);

            // act & assert
            assertThrows(CoreException.class, () -> template.update(null, null, FUTURE.plusDays(10)));
        }

        @DisplayName("name이 빈 문자열이면 예외가 발생한다.")
        @Test
        void throwsException_whenUpdateNameIsBlank() {
            // arrange
            CouponTemplateEntity template = new CouponTemplateEntity(VALID_NAME, VALID_TYPE, VALID_VALUE, null, FUTURE);

            // act & assert
            assertThrows(CoreException.class, () -> template.update("", null, FUTURE.plusDays(10)));
        }

    }
}
