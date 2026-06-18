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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTemplateModelTest {

    private static final String NAME = "신규가입 10% 할인";
    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(1);
    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);

    @DisplayName("쿠폰 템플릿을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 정액(FIXED) 값이면, 템플릿이 생성된다.")
        @Test
        void createsFixed_whenValid() {
            CouponTemplateModel template = new CouponTemplateModel(NAME, CouponType.FIXED, 3000L, 10000L, FUTURE);

            assertThat(template.getName()).isEqualTo(NAME);
            assertThat(template.getType()).isEqualTo(CouponType.FIXED);
            assertThat(template.getValue()).isEqualTo(3000L);
            assertThat(template.getMinOrderAmount()).isEqualTo(10000L);
            assertThat(template.getExpiredAt()).isEqualTo(FUTURE);
            assertThat(template.getDeletedAt()).isNull();
        }

        @DisplayName("유효한 정률(RATE) 값이면, 템플릿이 생성된다.")
        @Test
        void createsRate_whenValid() {
            CouponTemplateModel template = new CouponTemplateModel(NAME, CouponType.RATE, 10L, null, FUTURE);

            assertThat(template.getType()).isEqualTo(CouponType.RATE);
            assertThat(template.getValue()).isEqualTo(10L);
            assertThat(template.getMinOrderAmount()).isNull();
        }

        @DisplayName("name 이 null/공백이면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void throwsBadRequest_whenNameIsBlank(String invalidName) {
            CoreException ex = assertThrows(CoreException.class, () ->
                new CouponTemplateModel(invalidName, CouponType.FIXED, 3000L, null, FUTURE)
            );

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("type 이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTypeIsNull() {
            CoreException ex = assertThrows(CoreException.class, () ->
                new CouponTemplateModel(NAME, null, 3000L, null, FUTURE)
            );

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("expiredAt 이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpiredAtIsNull() {
            CoreException ex = assertThrows(CoreException.class, () ->
                new CouponTemplateModel(NAME, CouponType.FIXED, 3000L, null, null)
            );

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("FIXED 인데 value 가 0 이하이면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, -1000L})
        void throwsBadRequest_whenFixedValueIsNotPositive(long invalidValue) {
            CoreException ex = assertThrows(CoreException.class, () ->
                new CouponTemplateModel(NAME, CouponType.FIXED, invalidValue, null, FUTURE)
            );

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("RATE 인데 value 가 1~100 범위를 벗어나면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = {0L, -5L, 101L, 200L})
        void throwsBadRequest_whenRateValueOutOfRange(long invalidRate) {
            CoreException ex = assertThrows(CoreException.class, () ->
                new CouponTemplateModel(NAME, CouponType.RATE, invalidRate, null, FUTURE)
            );

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("할인액을 계산할 때, ")
    @Nested
    class CalculateDiscount {

        @DisplayName("FIXED 는 원금이 충분하면 정액 할인액을 반환한다.")
        @Test
        void returnsFixedAmount() {
            CouponTemplateModel template = new CouponTemplateModel(NAME, CouponType.FIXED, 3000L, null, FUTURE);

            assertThat(template.calculateDiscount(10000L)).isEqualTo(3000L);
        }

        @DisplayName("RATE 는 원금 × 비율을 버림(floor)한 할인액을 반환한다.")
        @Test
        void returnsFlooredRateAmount() {
            CouponTemplateModel template = new CouponTemplateModel(NAME, CouponType.RATE, 10L, null, FUTURE);

            // 9999 * 10 / 100 = 999.9 → 999 (버림)
            assertThat(template.calculateDiscount(9999L)).isEqualTo(999L);
        }

        @DisplayName("할인액은 원금을 초과할 수 없다. (cap)")
        @Test
        void capsAtOriginalAmount() {
            CouponTemplateModel template = new CouponTemplateModel(NAME, CouponType.FIXED, 50000L, null, FUTURE);

            assertThat(template.calculateDiscount(3000L)).isEqualTo(3000L);
        }
    }

    @DisplayName("만료 여부를 판정할 때, ")
    @Nested
    class IsExpired {

        @DisplayName("expiredAt 이 기준 시각 이전이면, 만료로 판정한다.")
        @Test
        void expired_whenPast() {
            CouponTemplateModel template = new CouponTemplateModel(NAME, CouponType.FIXED, 3000L, null, PAST);

            assertThat(template.isExpired(ZonedDateTime.now())).isTrue();
        }

        @DisplayName("expiredAt 이 기준 시각 이후이면, 만료가 아니다.")
        @Test
        void notExpired_whenFuture() {
            CouponTemplateModel template = new CouponTemplateModel(NAME, CouponType.FIXED, 3000L, null, FUTURE);

            assertThat(template.isExpired(ZonedDateTime.now())).isFalse();
        }
    }

    @DisplayName("최소 주문 금액 충족 여부를 판정할 때, ")
    @Nested
    class MeetsMinOrderAmount {

        @DisplayName("minOrderAmount 가 null 이면, 항상 충족한다.")
        @Test
        void alwaysMeets_whenNull() {
            CouponTemplateModel template = new CouponTemplateModel(NAME, CouponType.FIXED, 3000L, null, FUTURE);

            assertThat(template.meetsMinOrderAmount(0L)).isTrue();
        }

        @DisplayName("원금이 minOrderAmount 이상이면 충족, 미만이면 미충족.")
        @Test
        void checksThreshold() {
            CouponTemplateModel template = new CouponTemplateModel(NAME, CouponType.FIXED, 3000L, 10000L, FUTURE);

            assertThat(template.meetsMinOrderAmount(10000L)).isTrue();
            assertThat(template.meetsMinOrderAmount(9999L)).isFalse();
        }
    }
}
