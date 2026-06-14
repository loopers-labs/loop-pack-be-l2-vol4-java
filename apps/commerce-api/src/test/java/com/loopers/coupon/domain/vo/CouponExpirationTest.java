package com.loopers.coupon.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponExpirationTest {

    @DisplayName("만료일이 주어지면, 쿠폰 만료일을 생성한다.")
    @Test
    void createsCouponExpiration_whenExpiredAtIsProvided() {
        // arrange
        ZonedDateTime expiredAt = ZonedDateTime.parse("2026-12-31T23:59:59+09:00");

        // act
        CouponExpiration expiration = CouponExpiration.of(expiredAt);

        // assert
        assertThat(expiration.expiredAt()).isEqualTo(expiredAt);
    }

    @DisplayName("만료일이 없으면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenExpiredAtIsNull() {
        // arrange
        ZonedDateTime expiredAt = null;

        // act & assert
        assertThatThrownBy(() -> CouponExpiration.of(expiredAt))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("현재 시각이 만료일 이후이면, 만료된 것으로 판단한다.")
    @Test
    void returnsTrue_whenNowIsAfterExpiredAt() {
        // arrange
        CouponExpiration expiration = CouponExpiration.of(ZonedDateTime.parse("2026-12-31T23:59:59+09:00"));
        ZonedDateTime now = ZonedDateTime.parse("2027-01-01T00:00:00+09:00");

        // act
        boolean expired = expiration.isExpiredAt(now);

        // assert
        assertThat(expired).isTrue();
    }
}
