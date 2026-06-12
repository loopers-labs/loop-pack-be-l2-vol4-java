package com.loopers.domain.coupon;

import com.loopers.domain.vo.Money;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CouponModelTest {

    private static final ZonedDateTime EXPIRED_AT =
            ZonedDateTime.of(2026, 12, 31, 23, 59, 59, 0, ZoneId.of("Asia/Seoul"));
    private static final DiscountPolicy POLICY =
            DiscountPolicy.of(DiscountType.RATE, 10, Money.of(0));

    @Test
    void issuesAvailableUserCoupon_snapshottingPolicyAndExpiry() {
        // arrange — 10% 정률 템플릿
        CouponModel template = CouponModel.create("신규가입 10% 할인", POLICY, EXPIRED_AT);

        // act — 유저 1에게 발급
        UserCouponModel issued = template.issue(1L);

        // assert — 발급분은 AVAILABLE, 템플릿의 할인규칙/만료일을 그대로 스냅샷
        assertThat(issued.getUserId()).isEqualTo(1L);
        assertThat(issued.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
        assertThat(issued.getExpiredAt()).isEqualTo(EXPIRED_AT);
        assertThat(issued.getDiscountPolicy().calculateDiscount(Money.of(20_000)))
                .isEqualTo(Money.of(2_000));
    }
}
