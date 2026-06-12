package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponStatus;

import java.time.LocalDateTime;

public final class CouponInfo {

    private CouponInfo() {}

    public record Template(
        Long id,
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        LocalDateTime expiredAt
    ) {
        public static Template from(CouponTemplate template) {
            return new Template(
                template.getId(),
                template.getName(),
                template.getType(),
                template.getValue(),
                template.getMinOrderAmount(),
                template.getExpiredAt()
            );
        }
    }

    public record MyCoupon(
        Long id,
        Long couponTemplateId,
        UserCouponStatus status,
        LocalDateTime issuedAt,
        LocalDateTime usedAt
    ) {
        public static MyCoupon from(UserCoupon userCoupon) {
            return new MyCoupon(
                userCoupon.getId(),
                userCoupon.getCouponTemplateId(),
                userCoupon.getStatus(),
                userCoupon.getIssuedAt(),
                userCoupon.getUsedAt()
            );
        }
    }
}
