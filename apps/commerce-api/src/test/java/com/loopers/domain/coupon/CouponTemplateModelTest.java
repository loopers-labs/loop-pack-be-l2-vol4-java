package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTemplateModelTest {

    private static void setIssuedQuantity(CouponTemplateModel template, int issuedQuantity) throws Exception {
        Field field = CouponTemplateModel.class.getDeclaredField("issuedQuantity");
        field.setAccessible(true);
        field.set(template, issuedQuantity);
    }

    @DisplayName("쿠폰 템플릿을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("정상 입력으로 쿠폰 템플릿이 생성되면 입력값이 설정된다.")
        @Test
        void couponTemplateIsCreated_withGivenValues() {
            // given
            String name = "신규가입 10% 할인";
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(30);

            // when
            CouponTemplateModel template = new CouponTemplateModel(
                    name, CouponType.RATE, BigDecimal.valueOf(10), BigDecimal.valueOf(10000), expiredAt, 100);

            // then
            assertAll(
                    () -> assertThat(template.getName()).isEqualTo(name),
                    () -> assertThat(template.getDiscountPolicy().type()).isEqualTo(CouponType.RATE),
                    () -> assertThat(template.getDiscountPolicy().value()).isEqualByComparingTo(BigDecimal.valueOf(10)),
                    () -> assertThat(template.getMinOrderAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000)),
                    () -> assertThat(template.getExpiredAt()).isEqualTo(expiredAt),
                    () -> assertThat(template.getTotalQuantity()).isEqualTo(100),
                    () -> assertThat(template.getIssuedQuantity()).isEqualTo(0)
            );
        }

        @DisplayName("쿠폰 이름이 null이거나 빈 문자열이면 쿠폰 템플릿을 생성할 수 없다.")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        @ParameterizedTest
        void couponTemplateCannotBeCreated_whenNameIsNullOrBlank(String name) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new CouponTemplateModel(
                            name, CouponType.RATE, BigDecimal.valueOf(10), null, ZonedDateTime.now().plusDays(30), 100));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료일이 없으면 쿠폰 템플릿을 생성할 수 없다.")
        @NullSource
        @ParameterizedTest
        void couponTemplateCannotBeCreated_whenExpiredAtIsNull(ZonedDateTime expiredAt) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new CouponTemplateModel(
                            "신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10), null, expiredAt, 100));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("발급 가능 수량이 0 이하이면 쿠폰 템플릿을 생성할 수 없다.")
        @ValueSource(ints = {0, -1})
        @ParameterizedTest
        void couponTemplateCannotBeCreated_whenTotalQuantityIsNotPositive(int totalQuantity) {
            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> new CouponTemplateModel(
                            "신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10), null,
                            ZonedDateTime.now().plusDays(30), totalQuantity));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰 템플릿을 수정할 때,")
    @Nested
    class Update {

        @DisplayName("정상 입력으로 수정하면 이름·할인 정책·최소 주문 금액·만료일·발급 가능 수량이 새 값으로 변경된다.")
        @Test
        void couponTemplateIsUpdated_withNewValues() {
            // given
            CouponTemplateModel template = new CouponTemplateModel(
                    "신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10), BigDecimal.valueOf(10000),
                    ZonedDateTime.now().plusDays(30), 100);
            ZonedDateTime newExpiredAt = ZonedDateTime.now().plusDays(60);

            // when
            template.update("여름 시즌 5000원 할인", CouponType.FIXED, BigDecimal.valueOf(5000),
                    BigDecimal.valueOf(20000), newExpiredAt, 200);

            // then
            assertAll(
                    () -> assertThat(template.getName()).isEqualTo("여름 시즌 5000원 할인"),
                    () -> assertThat(template.getDiscountPolicy().type()).isEqualTo(CouponType.FIXED),
                    () -> assertThat(template.getDiscountPolicy().value()).isEqualByComparingTo(BigDecimal.valueOf(5000)),
                    () -> assertThat(template.getMinOrderAmount()).isEqualByComparingTo(BigDecimal.valueOf(20000)),
                    () -> assertThat(template.getExpiredAt()).isEqualTo(newExpiredAt),
                    () -> assertThat(template.getTotalQuantity()).isEqualTo(200)
            );
        }

        @DisplayName("이름이 null이거나 빈 문자열이면 수정할 수 없다.")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  "})
        @ParameterizedTest
        void couponTemplateCannotBeUpdated_whenNameIsNullOrBlank(String name) {
            // given
            CouponTemplateModel template = new CouponTemplateModel(
                    "신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10), null,
                    ZonedDateTime.now().plusDays(30), 100);

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> template.update(name, CouponType.FIXED, BigDecimal.valueOf(5000),
                            null, ZonedDateTime.now().plusDays(60), 100));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료일이 없으면 수정할 수 없다.")
        @NullSource
        @ParameterizedTest
        void couponTemplateCannotBeUpdated_whenExpiredAtIsNull(ZonedDateTime expiredAt) {
            // given
            CouponTemplateModel template = new CouponTemplateModel(
                    "신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10), null,
                    ZonedDateTime.now().plusDays(30), 100);

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> template.update("여름 시즌 5000원 할인", CouponType.FIXED, BigDecimal.valueOf(5000),
                            null, expiredAt, 100));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("발급 가능 수량이 0 이하이면 수정할 수 없다.")
        @Test
        void couponTemplateCannotBeUpdated_whenTotalQuantityIsNotPositive() {
            // given
            CouponTemplateModel template = new CouponTemplateModel(
                    "신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10), null,
                    ZonedDateTime.now().plusDays(30), 100);

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> template.update("신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10),
                            null, ZonedDateTime.now().plusDays(30), 0));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이미 발급된 수량보다 적은 수량으로는 수정할 수 없다.")
        @Test
        void couponTemplateCannotBeUpdated_whenTotalQuantityIsBelowIssuedQuantity() throws Exception {
            // given
            CouponTemplateModel template = new CouponTemplateModel(
                    "신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10), null,
                    ZonedDateTime.now().plusDays(30), 100);
            setIssuedQuantity(template, 50);

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> template.update("신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10),
                            null, ZonedDateTime.now().plusDays(30), 49));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰 적용 가능 여부를 검증할 때,")
    @Nested
    class ValidateApplicability {

        @DisplayName("만료되지 않고 최소 주문 금액 이상이면 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenNotExpiredAndMeetsMinOrderAmount() {
            // given
            CouponTemplateModel template = new CouponTemplateModel(
                    "할인 쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000),
                    BigDecimal.valueOf(5000), ZonedDateTime.now().plusDays(1), 100);

            // when & then
            assertDoesNotThrow(() -> template.validateApplicability(BigDecimal.valueOf(10000)));
        }

        @DisplayName("최소 주문 금액이 null이면 금액 조건 없이 검증을 통과한다.")
        @Test
        void doesNotThrow_whenMinOrderAmountIsNull() {
            // given
            CouponTemplateModel template = new CouponTemplateModel(
                    "할인 쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000),
                    null, ZonedDateTime.now().plusDays(1), 100);

            // when & then
            assertDoesNotThrow(() -> template.validateApplicability(BigDecimal.valueOf(1000)));
        }

        @DisplayName("만료된 쿠폰이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponIsExpired() {
            // given
            CouponTemplateModel template = new CouponTemplateModel(
                    "만료 쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000),
                    null, ZonedDateTime.now().minusDays(1), 100);

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> template.validateApplicability(BigDecimal.valueOf(10000)));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("최소 주문 금액을 충족하지 않으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOriginalPriceBelowMinOrderAmount() {
            // given
            CouponTemplateModel template = new CouponTemplateModel(
                    "할인 쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000),
                    BigDecimal.valueOf(10000), ZonedDateTime.now().plusDays(1), 100);

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> template.validateApplicability(BigDecimal.valueOf(5000)));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰 템플릿 만료 여부를 확인할 때,")
    @Nested
    class IsExpired {

        @DisplayName("만료일이 현재 시각 이후이면 아직 만료되지 않은 쿠폰이다.")
        @Test
        void couponTemplateIsNotYetExpired_whenExpiredAtIsInFuture() {
            // given
            CouponTemplateModel template = new CouponTemplateModel(
                    "신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10), null,
                    ZonedDateTime.now().plusDays(1), 100);

            // when / then
            assertThat(template.isExpired()).isFalse();
        }

        @DisplayName("만료일이 현재 시각 이전이면 만료된 쿠폰이다.")
        @Test
        void couponTemplateIsExpired_whenExpiredAtIsInPast() {
            // given
            CouponTemplateModel template = new CouponTemplateModel(
                    "신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10), null,
                    ZonedDateTime.now().minusDays(1), 100);

            // when / then
            assertThat(template.isExpired()).isTrue();
        }
    }
}
