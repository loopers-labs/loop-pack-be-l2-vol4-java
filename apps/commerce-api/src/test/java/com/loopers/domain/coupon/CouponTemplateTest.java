package com.loopers.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CouponTemplateTest {

    @Test
    @DisplayName("쿠폰 템플릿 만료 여부를 검증할 수 있다.")
    void isExpired_ShouldReturnCorrectStatus() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 6, 11, 21, 0);
        CouponTemplate expiredTemplate = new CouponTemplate("만료쿠폰", CouponType.FIXED, new BigDecimal("1000"), null, null, now.minusSeconds(1));
        CouponTemplate activeTemplate = new CouponTemplate("사용가능쿠폰", CouponType.FIXED, new BigDecimal("1000"), null, null, now.plusDays(1));

        // when & then
        assertThat(expiredTemplate.isExpired(now)).isTrue();
        assertThat(activeTemplate.isExpired(now)).isFalse();
    }
}
