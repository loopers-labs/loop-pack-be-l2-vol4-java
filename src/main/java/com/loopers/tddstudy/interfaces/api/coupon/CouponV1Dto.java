package com.loopers.tddstudy.interfaces.api.coupon;

public class CouponV1Dto {

    // 쿠폰 발급 요청 (body 없음, couponId는 path로 받음)

    // 내 쿠폰 목록 응답
    public record UserCouponResponse(
            Long userCouponId,
            Long couponId,
            String couponName,
            String type,        // FIXED | RATE
            int value,
            String status       // AVAILABLE | USED | EXPIRED
    ) {}
}
