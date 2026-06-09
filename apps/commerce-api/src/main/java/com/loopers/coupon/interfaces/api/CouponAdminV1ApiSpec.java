package com.loopers.coupon.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Coupon Admin V1 API", description = "Loopers 쿠폰 템플릿 관리자 API 입니다. (X-Loopers-Admin-Id 헤더 필요)")
public interface CouponAdminV1ApiSpec {

    @Operation(
            summary = "쿠폰 템플릿 등록",
            description = "이름, 타입(FIXED/RATE), 값, 최소 주문 금액, 만료 시각으로 새 쿠폰 템플릿을 등록합니다."
    )
    ApiResponse<CouponAdminV1Response.Detail> create(@Valid CouponAdminV1Request.Create request);

    @Operation(
            summary = "쿠폰 템플릿 단건 조회",
            description = "couponId 로 쿠폰 템플릿 상세를 조회합니다."
    )
    ApiResponse<CouponAdminV1Response.Detail> get(Long couponId);

    @Operation(
            summary = "쿠폰 템플릿 목록 조회",
            description = "쿠폰 템플릿을 페이지 단위로 조회합니다."
    )
    ApiResponse<CouponAdminV1Response.Page> getAll(int page, int size);

    @Operation(
            summary = "쿠폰 템플릿 수정",
            description = "couponId 의 이름, 타입, 값, 최소 주문 금액, 만료 시각을 변경합니다."
    )
    ApiResponse<CouponAdminV1Response.Detail> update(Long couponId, @Valid CouponAdminV1Request.Update request);

    @Operation(
            summary = "쿠폰 템플릿 삭제",
            description = "couponId 의 쿠폰 템플릿을 soft delete 합니다. 이미 발급된 쿠폰은 영향받지 않습니다."
    )
    ApiResponse<Void> delete(Long couponId);
}
