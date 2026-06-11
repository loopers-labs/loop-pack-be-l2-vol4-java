package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Coupon V1 API", description = "쿠폰 발급/조회 API 입니다.")
public interface CouponV1ApiSpec {

    @Operation(summary = "쿠폰 발급", description = "쿠폰 템플릿 ID로 헤더로 식별한 유저에게 쿠폰을 발급합니다. 발급 시점 정책이 스냅샷으로 저장됩니다.")
    ApiResponse<CouponV1Dto.UserCouponResponse> issueCoupon(AuthHeaders auth, Long couponId);

    @Operation(summary = "내 쿠폰 목록", description = "보유 쿠폰을 상태(AVAILABLE/USED/EXPIRED)와 함께 반환합니다.")
    ApiResponse<List<CouponV1Dto.UserCouponResponse>> getMyCoupons(AuthHeaders auth);
}
