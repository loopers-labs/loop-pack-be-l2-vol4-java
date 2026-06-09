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

    @Operation(
        summary = "쿠폰 템플릿 수정",
        description = "관리자가 쿠폰 템플릿 정보를 수정하고 식별자를 반환한다. 수정은 이후 발급분에만 영향을 준다."
    )
    ApiResponse<CouponAdminV1Dto.UpdateResponse> updateCoupon(Long couponId, CouponAdminV1Dto.UpdateRequest request);

    @Operation(
        summary = "쿠폰 템플릿 삭제",
        description = "관리자가 쿠폰 템플릿을 삭제(soft delete)한다. 존재하지 않거나 이미 삭제된 템플릿도 정상 응답으로 마무리한다(멱등). 발급된 쿠폰은 스냅샷으로 독립 동작한다."
    )
    ApiResponse<Void> deleteCoupon(Long couponId);

    @Operation(
        summary = "쿠폰 템플릿 목록 조회",
        description = "관리자가 삭제되지 않은 쿠폰 템플릿을 등록 시각 내림차순으로 페이징 조회한다. 만료된 템플릿도 포함된다."
    )
    ApiResponse<CouponAdminV1Dto.PageResponse> readCoupons(int page, int size);

    @Operation(
        summary = "쿠폰 템플릿 상세 조회",
        description = "관리자가 특정 쿠폰 템플릿의 상세 정보를 조회한다. 만료된 템플릿도 정상 조회되며, 삭제된 템플릿은 찾을 수 없다."
    )
    ApiResponse<CouponAdminV1Dto.DetailResponse> readCoupon(Long couponId);
}
