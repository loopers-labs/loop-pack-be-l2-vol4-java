package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.UserCouponModel;

import java.time.ZonedDateTime;

/**
 * 발급된 사용자 쿠폰 정보 DTO (대고객 화면용).
 *
 * <p>혜택 정보(이름/타입/값/최소금액)는 발급 시점 스냅샷에서 읽는다 — 어드민이 템플릿을
 * 수정해도 발급분의 표시 혜택은 변하지 않으며, 템플릿 조회 없이 발급분만으로 완성된다.
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
    public static UserCouponInfo from(UserCouponModel userCoupon, ZonedDateTime now) {
        CouponStatus displayStatus = userCoupon.displayStatus(now);
        return new UserCouponInfo(
            userCoupon.getId(),
            userCoupon.getCouponId(),
            userCoupon.getCouponName(),
            userCoupon.getType().name(),
            userCoupon.getValue(),
            userCoupon.getMinOrderAmount(),
            displayStatus.name(),
            userCoupon.getExpiredAt(),
            userCoupon.getUsedAt()
        );
    }
}
