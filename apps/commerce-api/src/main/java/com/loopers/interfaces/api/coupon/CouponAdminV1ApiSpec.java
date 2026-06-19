package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupon Admin V1 API", description = "쿠폰 템플릿 관리 API 입니다.")
public interface CouponAdminV1ApiSpec {

    @Operation(summary = "쿠폰 템플릿 목록 조회", description = "등록된 쿠폰 템플릿 목록을 페이지네이션으로 조회합니다.")
    ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>> getCoupons(String ldap, int page, int size);

    @Operation(summary = "쿠폰 템플릿 상세 조회", description = "쿠폰 템플릿 단건을 조회합니다.")
    ApiResponse<CouponAdminV1Dto.CouponResponse> getCoupon(String ldap, Long couponId);

    @Operation(summary = "쿠폰 템플릿 등록", description = "새 쿠폰 템플릿을 등록합니다.")
    ApiResponse<CouponAdminV1Dto.CouponResponse> createCoupon(String ldap, CouponAdminV1Dto.CouponCreateRequest request);

    @Operation(summary = "쿠폰 템플릿 수정", description = "쿠폰 템플릿을 수정합니다.")
    ApiResponse<CouponAdminV1Dto.CouponResponse> updateCoupon(String ldap, Long couponId, CouponAdminV1Dto.CouponUpdateRequest request);

    @Operation(summary = "쿠폰 템플릿 삭제", description = "쿠폰 템플릿을 삭제합니다.")
    ApiResponse<Void> deleteCoupon(String ldap, Long couponId);

    @Operation(summary = "쿠폰 발급 내역 조회", description = "특정 쿠폰 템플릿의 발급 내역을 페이지네이션으로 조회합니다.")
    ApiResponse<PageResponse<CouponAdminV1Dto.IssuedCouponResponse>> getIssuedCoupons(String ldap, Long couponId, int page, int size);
}
