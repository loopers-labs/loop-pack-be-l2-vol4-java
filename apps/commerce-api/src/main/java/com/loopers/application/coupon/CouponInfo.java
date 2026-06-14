package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.Discount;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 쿠폰 템플릿 뷰 DTO. admin 화면에서 템플릿 자체를 보여줄 때 쓴다.
 * (UserCouponInfo 와 달리 userId/status 가 없다 — 발급분이 아니라 정책 원본이므로)
 */
public record CouponInfo(
    Long id,
    String name,
    CouponType type,
    long value,
    BigDecimal minOrderAmount,
    LocalDateTime expiredAt
) {
    public static CouponInfo from(Coupon coupon) {
        Discount discount = coupon.getDiscount();
        return new CouponInfo(
            coupon.getId(),
            coupon.getName(),
            discount.getType(),
            discount.getValue(),
            coupon.getMinOrderAmount().getAmount(),
            coupon.getExpiredAt()
        );
    }
}
