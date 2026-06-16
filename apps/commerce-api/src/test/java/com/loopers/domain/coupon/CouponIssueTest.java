package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponIssueTest {

    @Test
    @DisplayName("최소 주문 금액보다 주문 금액이 적으면 쿠폰 사용 시 예외가 발생한다.")
    void use_UnderMinOrderAmount_ShouldThrowException() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 6, 11, 21, 0);
        CouponTemplate template = new CouponTemplate("쿠폰", CouponType.FIXED, new BigDecimal("1000"), new BigDecimal("10000"), null, now.plusDays(1));
        // template에 id 세팅을 시뮬레이션하기 위해 reflection 사용 (JPA 의존성 없이)
        org.springframework.test.util.ReflectionTestUtils.setField(template, "id", 10L);
        CouponIssue issue = new CouponIssue(1L, template);

        // when & then
        assertThrows(CoreException.class, () -> issue.use(new BigDecimal("9999"), now));
    }

    @Test
    @DisplayName("이미 사용된(USED) 쿠폰을 사용하려고 하면 예외가 발생한다.")
    void use_AlreadyUsed_ShouldThrowException() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 6, 11, 21, 0);
        CouponTemplate template = new CouponTemplate("쿠폰", CouponType.FIXED, new BigDecimal("1000"), BigDecimal.ZERO, null, now.plusDays(1));
        org.springframework.test.util.ReflectionTestUtils.setField(template, "id", 10L);
        CouponIssue issue = new CouponIssue(1L, template);

        // 첫 번째 사용으로 USED 상태가 됨
        issue.use(new BigDecimal("5000"), now);

        // 두 번째 사용 시도 시 예외 발생
        assertThrows(CoreException.class, () -> issue.use(new BigDecimal("5000"), now));
    }

    @Test
    @DisplayName("정액(FIXED) 쿠폰은 고정된 금액만큼 할인되며 주문 금액을 초과해 할인될 수 없다.")
    void use_FixedCoupon_ShouldCalculateDiscount() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 6, 11, 21, 0);
        CouponTemplate template = new CouponTemplate("천원할인", CouponType.FIXED, new BigDecimal("1000"), BigDecimal.ZERO, null, now.plusDays(1));
        org.springframework.test.util.ReflectionTestUtils.setField(template, "id", 10L);
        
        // 1번 케이스: 5000원 주문에 1000원 할인
        CouponIssue issue1 = new CouponIssue(1L, template);
        BigDecimal discount = issue1.use(new BigDecimal("5000"), now);
        assertThat(discount).isEqualByComparingTo("1000");

        // 2번 케이스: 500원 주문에 500원 할인 (주문 금액 초과 불가)
        CouponIssue issue2 = new CouponIssue(1L, template);
        BigDecimal discountExceed = issue2.use(new BigDecimal("500"), now);
        assertThat(discountExceed).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("정률(RATE) 쿠폰은 비율만큼 할인되며 최대 한도(maxDiscountAmount)가 있으면 적용된다.")
    void use_RateCoupon_ShouldCalculateDiscountWithLimit() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 6, 11, 21, 0);
        CouponTemplate template = new CouponTemplate("10%할인", CouponType.RATE, new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("2000"), now.plusDays(1));
        org.springframework.test.util.ReflectionTestUtils.setField(template, "id", 10L);
        
        CouponIssue issue1 = new CouponIssue(1L, template);
        CouponIssue issue2 = new CouponIssue(1L, template);

        // when
        BigDecimal discount1 = issue1.use(new BigDecimal("10000"), now);
        BigDecimal discount2 = issue2.use(new BigDecimal("30000"), now);

        // then
        assertThat(discount1).isEqualByComparingTo("1000");
        assertThat(discount2).isEqualByComparingTo("2000");
    }
}
