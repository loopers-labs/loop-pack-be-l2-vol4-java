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

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTemplateModelTest {

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
                    name, CouponType.RATE, BigDecimal.valueOf(10), BigDecimal.valueOf(10000), expiredAt);

            // then
            assertAll(
                    () -> assertThat(template.getName()).isEqualTo(name),
                    () -> assertThat(template.getDiscountPolicy().type()).isEqualTo(CouponType.RATE),
                    () -> assertThat(template.getDiscountPolicy().value()).isEqualByComparingTo(BigDecimal.valueOf(10)),
                    () -> assertThat(template.getMinOrderAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000)),
                    () -> assertThat(template.getExpiredAt()).isEqualTo(expiredAt)
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
                            name, CouponType.RATE, BigDecimal.valueOf(10), null, ZonedDateTime.now().plusDays(30)));

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
                            "신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10), null, expiredAt));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰 템플릿을 수정할 때,")
    @Nested
    class Update {

        @DisplayName("정상 입력으로 수정하면 이름·할인 정책·최소 주문 금액·만료일이 새 값으로 변경된다.")
        @Test
        void couponTemplateIsUpdated_withNewValues() {
            // given
            CouponTemplateModel template = new CouponTemplateModel(
                    "신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10), BigDecimal.valueOf(10000),
                    ZonedDateTime.now().plusDays(30));
            ZonedDateTime newExpiredAt = ZonedDateTime.now().plusDays(60);

            // when
            template.update("여름 시즌 5000원 할인", CouponType.FIXED, BigDecimal.valueOf(5000),
                    BigDecimal.valueOf(20000), newExpiredAt);

            // then
            assertAll(
                    () -> assertThat(template.getName()).isEqualTo("여름 시즌 5000원 할인"),
                    () -> assertThat(template.getDiscountPolicy().type()).isEqualTo(CouponType.FIXED),
                    () -> assertThat(template.getDiscountPolicy().value()).isEqualByComparingTo(BigDecimal.valueOf(5000)),
                    () -> assertThat(template.getMinOrderAmount()).isEqualByComparingTo(BigDecimal.valueOf(20000)),
                    () -> assertThat(template.getExpiredAt()).isEqualTo(newExpiredAt)
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
                    ZonedDateTime.now().plusDays(30));

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> template.update(name, CouponType.FIXED, BigDecimal.valueOf(5000),
                            null, ZonedDateTime.now().plusDays(60)));

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
                    ZonedDateTime.now().plusDays(30));

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> template.update("여름 시즌 5000원 할인", CouponType.FIXED, BigDecimal.valueOf(5000),
                            null, expiredAt));

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
                    ZonedDateTime.now().plusDays(1));

            // when / then
            assertThat(template.isExpired()).isFalse();
        }

        @DisplayName("만료일이 현재 시각 이전이면 만료된 쿠폰이다.")
        @Test
        void couponTemplateIsExpired_whenExpiredAtIsInPast() {
            // given
            CouponTemplateModel template = new CouponTemplateModel(
                    "신규가입 10% 할인", CouponType.RATE, BigDecimal.valueOf(10), null,
                    ZonedDateTime.now().minusDays(1));

            // when / then
            assertThat(template.isExpired()).isTrue();
        }
    }
}
