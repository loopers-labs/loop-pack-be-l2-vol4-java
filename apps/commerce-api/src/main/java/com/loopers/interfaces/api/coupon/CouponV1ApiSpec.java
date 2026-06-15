package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupon V1 API", description = "Loopers 쿠폰 대고객 API 입니다.")
public interface CouponV1ApiSpec {

    @Operation(
            summary = "쿠폰 발급",
            description = "쿠폰 템플릿(couponId)으로 로그인 사용자의 쿠폰을 발급합니다. 한 사용자는 같은 템플릿을 1장만 가질 수 있습니다."
    )
    ApiResponse<CouponV1Dto.IssuedResponse> issue(Long userId, Long couponId);

    @Operation(
            summary = "내 쿠폰 목록 조회",
            description = "로그인 사용자가 보유한 쿠폰을 상태(AVAILABLE/USED/EXPIRED)와 함께 페이징하여 반환합니다."
    )
    ApiResponse<CouponV1Dto.MyCouponPageResponse> getMyCoupons(Long userId, int page, int size);
}
