package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.UserCouponInfo;

import java.time.ZonedDateTime;
import java.util.List;

public class CouponV1Dto {

    public record UserCouponResponse(
            Long id,
            Long couponId,
            String couponName,
            String couponType,
            Long value,
            Long minOrderAmount,
            ZonedDateTime expiredAt,
            String status,
            ZonedDateTime usedAt
    ) {
        public static UserCouponResponse from(UserCouponInfo info) {
            return new UserCouponResponse(
                    info.id(),
                    info.coupon().id(),
                    info.coupon().name(),
                    info.coupon().type(),
                    info.coupon().value(),
                    info.coupon().minOrderAmount(),
                    info.coupon().expiredAt(),
                    info.status(),
                    info.usedAt()
            );
        }

        public static List<UserCouponResponse> from(List<UserCouponInfo> infos) {
            return infos.stream().map(UserCouponResponse::from).toList();
        }
    }
}
