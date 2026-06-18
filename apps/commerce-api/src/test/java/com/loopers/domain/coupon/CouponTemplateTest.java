package com.loopers.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CouponTemplateTest {

    @Test
    @DisplayName("荑좏룿 ?쒗뵆由?留뚮즺 ?щ?瑜?寃利앺븷 ???덈떎.")
    void isExpired_ShouldReturnCorrectStatus() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 6, 11, 21, 0);
        CouponTemplate expiredTemplate = new CouponTemplate("留뚮즺荑좏룿", CouponType.FIXED, new BigDecimal("1000"), null, null, now.minusSeconds(1));
        CouponTemplate activeTemplate = new CouponTemplate("?ъ슜媛?μ퓼??, CouponType.FIXED, new BigDecimal("1000"), null, null, now.plusDays(1));

        // when & then
        assertThat(expiredTemplate.isExpired(now)).isTrue();
        assertThat(activeTemplate.isExpired(now)).isFalse();
    }
}
