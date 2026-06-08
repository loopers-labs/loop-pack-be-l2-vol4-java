package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.UserCouponModel;

import java.time.ZonedDateTime;

/**
 * 발급된 사용자 쿠폰 정보 DTO (대고객 화면용).
 *
 * <p>발급분의 상태(USED 등)와 템플릿의 할인 정보(이름/타입/값)를 함께 담는다.
 * 상태는 조회 시점 기준 표시 상태(AVAILABLE/USED/EXPIRED)로 계산된다.
 */
public record UserCouponInfo(
    Long id,
    Long couponId,
    String name,
    String type,
    long value,
    Long minOrderAmount,
    String status,
    ZonedDateTime expiredAt,
    ZonedDateTime usedAt
) {
    public static UserCouponInfo from(UserCouponModel userCoupon, CouponModel template, ZonedDateTime now) {
        CouponStatus displayStatus = userCoupon.displayStatus(now);
        return new UserCouponInfo(
            userCoupon.getId(),
            userCoupon.getCouponId(),
            template.getName(),
            template.getType().name(),
            template.getValue(),
            template.getMinOrderAmount(),
            displayStatus.name(),
            userCoupon.getExpiredAt(),
            userCoupon.getUsedAt()
        );
    }
}
