package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Coupon V1 API", description = "쿠폰 API")
public interface CouponV1ApiSpec {

    @Operation(summary = "쿠폰 발급", description = "쿠폰 ID로 쿠폰을 발급합니다.")
    ApiResponse<CouponV1Dto.UserCouponResponse> issueCoupon(Long couponId, Long userId);

    @Operation(summary = "내 쿠폰 목록 조회", description = "발급받은 쿠폰 목록을 조회합니다.")
    ApiResponse<List<CouponV1Dto.UserCouponResponse>> getMyCoupons(Long userId);
}
