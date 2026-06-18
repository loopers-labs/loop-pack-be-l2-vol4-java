package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponStatus;

import java.time.ZonedDateTime;
import java.util.UUID;

public record UserCouponInfo(
    UUID id,
    UUID templateId,
    UUID userId,
    UserCouponStatus status,
    CouponType type,
    Long value,
    Long minOrderAmount,
    ZonedDateTime expiredAt,
    UUID orderId,
    ZonedDateTime usedAt,
    ZonedDateTime issuedAt
) {

    public static UserCouponInfo from(UserCouponModel model, ZonedDateTime now) {
        return new UserCouponInfo(
            model.getId(),
            model.getTemplateId(),
            model.getUserId(),
            model.displayStatus(now),
            model.getType(),
            model.getValue(),
            model.getMinOrderAmount(),
            model.getExpiredAt(),
            model.getOrderId(),
            model.getUsedAt(),
            model.getCreatedAt()
        );
    }
}
