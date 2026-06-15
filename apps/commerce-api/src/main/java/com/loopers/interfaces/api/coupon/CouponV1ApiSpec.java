package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Coupon V1 API", description = "쿠폰 관련 API")
public interface CouponV1ApiSpec {

    @Operation(summary = "쿠폰 발급", description = "쿠폰 템플릿으로부터 내게 쿠폰을 발급한다.")
    ApiResponse<CouponV1Dto.CouponResponse> issue(Long userId, Long couponId);

    @Operation(summary = "내 쿠폰 목록 조회", description = "내가 보유한 쿠폰을 상태(AVAILABLE/USED/EXPIRED)와 함께 조회한다.")
    ApiResponse<List<CouponV1Dto.CouponResponse>> getMyCoupons(Long userId);
}
