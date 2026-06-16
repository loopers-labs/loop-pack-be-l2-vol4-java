package com.loopers.coupon.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupon V1 API", description = "Loopers 쿠폰 사용자 API 입니다. (X-Loopers-LoginId/LoginPw 헤더 필요)")
public interface CouponV1ApiSpec {

    @Operation(
            summary = "쿠폰 발급",
            description = "couponId 템플릿으로부터 내 쿠폰 한 장을 발급합니다. 유저당 1회만 발급할 수 있습니다."
    )
    ApiResponse<CouponV1Response.IssueDetail> issue(Long userId, Long couponId);

    @Operation(
            summary = "내 쿠폰 목록 조회",
            description = "보유 쿠폰을 상태(AVAILABLE/USED/EXPIRED)와 함께 조회합니다."
    )
    ApiResponse<CouponV1Response.MyCoupons> getMyCoupons(Long userId);
}
