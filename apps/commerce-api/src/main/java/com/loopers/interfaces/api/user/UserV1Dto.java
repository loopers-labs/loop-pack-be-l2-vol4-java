package com.loopers.interfaces.api.user;

import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.application.user.UserInfo;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponStatus;

import java.time.ZonedDateTime;

public class UserV1Dto {

    public record CreateUserRequest(String loginId, String loginPw) {}

    public record UserResponse(Long id, String loginId) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(info.id(), info.loginId());
        }
    }

    public record CouponResponse(
        Long userCouponId,
        Long couponId,
        String couponName,
        CouponType discountType,
        Long discountValue,
        UserCouponStatus status,
        ZonedDateTime issuedAt
    ) {
        public static CouponResponse from(UserCouponInfo info) {
            return new CouponResponse(
                info.userCouponId(),
                info.couponId(),
                info.couponName(),
                info.discountType(),
                info.discountValue(),
                info.status(),
                info.issuedAt()
            );
        }
    }
}
