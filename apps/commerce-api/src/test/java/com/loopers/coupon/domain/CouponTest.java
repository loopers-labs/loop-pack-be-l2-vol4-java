package com.loopers.coupon.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class CouponTest {

    private static final ZonedDateTime EXPIRES = ZonedDateTime.parse("2026-12-31T23:59:59+09:00");

    @Test
    @DisplayName("create 로 생성하면 템플릿 정보가 저장된다")
    void givenValidInputs_whenCreate_thenStoresFields() {
        Coupon coupon = Coupon.create("신규가입 3천원", CouponType.FIXED, 3_000L, 10_000L, EXPIRES);

        assertAll(
                () -> assertThat(coupon.getName()).isEqualTo("신규가입 3천원"),
                () -> assertThat(coupon.getType()).isEqualTo(CouponType.FIXED),
                () -> assertThat(coupon.getValue()).isEqualTo(3_000L),
                () -> assertThat(coupon.getMinOrderAmount()).isEqualTo(10_000L),
                () -> assertThat(coupon.getExpiredAt()).isEqualTo(EXPIRES)
        );
    }

    @Test
    @DisplayName("최소 주문 금액은 생략(null)할 수 있다")
    void givenNullMinOrderAmount_whenCreate_thenAllowed() {
        Coupon coupon = Coupon.create("정률 10%", CouponType.RATE, 10L, null, EXPIRES);

        assertThat(coupon.getMinOrderAmount()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("이름이 비어 있으면 CoreException 이 발생한다")
    void givenBlankName_whenCreate_thenThrowsCoreException(String invalid) {
        assertThatThrownBy(() -> Coupon.create(invalid, CouponType.FIXED, 3_000L, null, EXPIRES))
                .isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("타입이 null 이면 CoreException 이 발생한다")
    void givenNullType_whenCreate_thenThrowsCoreException() {
        assertThatThrownBy(() -> Coupon.create("쿠폰", null, 3_000L, null, EXPIRES))
                .isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("만료 시각이 null 이면 CoreException 이 발생한다")
    void givenNullExpiredAt_whenCreate_thenThrowsCoreException() {
        assertThatThrownBy(() -> Coupon.create("쿠폰", CouponType.FIXED, 3_000L, null, null))
                .isInstanceOf(CoreException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("할인 값이 0 이하이면 INVALID_COUPON_VALUE 로 실패한다")
    void givenNonPositiveValue_whenCreate_thenThrowsInvalidCouponValue(long invalid) {
        assertThatThrownBy(() -> Coupon.create("쿠폰", CouponType.FIXED, invalid, null, EXPIRES))
                .isInstanceOf(CoreException.class)
                .extracting("errorCode")
                .isEqualTo(CouponErrorCode.INVALID_COUPON_VALUE);
    }

    @ParameterizedTest
    @ValueSource(longs = {101L, 200L})
    @DisplayName("정률(RATE) 값이 100 을 초과하면 INVALID_COUPON_VALUE 로 실패한다")
    void givenRateOver100_whenCreate_thenThrowsInvalidCouponValue(long invalid) {
        assertThatThrownBy(() -> Coupon.create("쿠폰", CouponType.RATE, invalid, null, EXPIRES))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @Test
    @DisplayName("정액(FIXED) 값은 100 을 초과해도 허용된다")
    void givenFixedOver100_whenCreate_thenAllowed() {
        Coupon coupon = Coupon.create("정액 5천원", CouponType.FIXED, 5_000L, null, EXPIRES);

        assertThat(coupon.getValue()).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("update 로 정책을 수정할 수 있다")
    void givenCoupon_whenUpdate_thenChangesFields() {
        Coupon coupon = Coupon.create("이름", CouponType.FIXED, 3_000L, null, EXPIRES);

        ZonedDateTime newExpiry = ZonedDateTime.parse("2027-06-30T23:59:59+09:00");
        coupon.update("정률 15%", CouponType.RATE, 15L, 20_000L, newExpiry);

        assertAll(
                () -> assertThat(coupon.getName()).isEqualTo("정률 15%"),
                () -> assertThat(coupon.getType()).isEqualTo(CouponType.RATE),
                () -> assertThat(coupon.getValue()).isEqualTo(15L),
                () -> assertThat(coupon.getMinOrderAmount()).isEqualTo(20_000L),
                () -> assertThat(coupon.getExpiredAt()).isEqualTo(newExpiry)
        );
    }

    @Test
    @DisplayName("update 시에도 값 검증이 적용된다")
    void givenInvalidValue_whenUpdate_thenThrowsCoreException() {
        Coupon coupon = Coupon.create("이름", CouponType.RATE, 10L, null, EXPIRES);

        assertThatThrownBy(() -> coupon.update("이름", CouponType.RATE, 150L, null, EXPIRES))
                .isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("isExpired 는 만료 시각이 현재보다 과거이면 true 를 반환한다")
    void givenExpiredAtBeforeNow_whenIsExpired_thenTrue() {
        Coupon coupon = Coupon.create("이름", CouponType.FIXED, 3_000L, null, EXPIRES);

        assertThat(coupon.isExpired(EXPIRES.plusSeconds(1))).isTrue();
        assertThat(coupon.isExpired(EXPIRES.minusSeconds(1))).isFalse();
    }
}
