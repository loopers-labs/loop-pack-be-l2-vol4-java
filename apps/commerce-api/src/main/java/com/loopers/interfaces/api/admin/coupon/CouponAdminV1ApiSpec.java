package com.loopers.interfaces.api.admin.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Coupon Admin V1 API", description = "쿠폰 템플릿 관리자 API 입니다. (X-Loopers-Ldap 헤더 필요)")
public interface CouponAdminV1ApiSpec {

    @Operation(summary = "쿠폰 템플릿 목록 조회", description = "페이지네이션으로 쿠폰 템플릿 목록을 조회합니다.")
    ApiResponse<List<CouponAdminV1Dto.CouponResponse>> getCoupons(int page, int size);

    @Operation(summary = "쿠폰 템플릿 상세 조회", description = "쿠폰 템플릿 단건을 조회합니다.")
    ApiResponse<CouponAdminV1Dto.CouponResponse> getCoupon(Long couponId);

    @Operation(summary = "쿠폰 템플릿 등록", description = "정액(FIXED)/정률(RATE) 쿠폰 템플릿을 등록합니다.")
    ApiResponse<CouponAdminV1Dto.CouponResponse> createCoupon(CouponAdminV1Dto.CouponRequest request);

    @Operation(summary = "쿠폰 템플릿 수정", description = "쿠폰 템플릿 정책을 수정합니다. 기발급 쿠폰에는 영향을 주지 않습니다.")
    ApiResponse<CouponAdminV1Dto.CouponResponse> updateCoupon(Long couponId, CouponAdminV1Dto.CouponRequest request);

    @Operation(summary = "쿠폰 템플릿 삭제", description = "soft delete 로 신규 발급을 중단합니다. 기발급 쿠폰은 계속 사용 가능합니다.")
    ApiResponse<Object> deleteCoupon(Long couponId);

    @Operation(summary = "쿠폰 발급 내역 조회", description = "특정 쿠폰 템플릿의 발급 내역을 페이지네이션으로 조회합니다.")
    ApiResponse<List<CouponAdminV1Dto.IssuedCouponResponse>> getIssuedCoupons(Long couponId, int page, int size);
}
