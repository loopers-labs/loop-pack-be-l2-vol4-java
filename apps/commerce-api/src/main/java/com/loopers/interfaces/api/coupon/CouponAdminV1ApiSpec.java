package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupon Admin V1 API", description = "Loopers 쿠폰 관리자 도메인 API 입니다.")
public interface CouponAdminV1ApiSpec {

    @Operation(
        summary = "쿠폰 템플릿 등록",
        description = "관리자가 새로운 쿠폰 템플릿을 등록하고 생성된 쿠폰 템플릿 식별자를 반환한다."
    )
    ApiResponse<CouponAdminV1Dto.CreateResponse> createCoupon(CouponAdminV1Dto.CreateRequest request);
}
