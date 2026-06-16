package com.loopers.interfaces.apiadmin.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;

@Tag(name = "Coupon Admin V1 API", description = "쿠폰 어드민 API")
public interface CouponAdminV1ApiSpec {

    @Operation(summary = "쿠폰 목록 조회", description = "등록된 쿠폰 목록을 페이지 단위로 조회합니다.")
    ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>> getList(Pageable pageable);

    @Operation(summary = "쿠폰 상세 조회", description = "쿠폰 ID로 쿠폰 상세 정보를 조회합니다.")
    ApiResponse<CouponAdminV1Dto.CouponResponse> getCoupon(Long couponId);

    @Operation(summary = "쿠폰 등록", description = "새로운 쿠폰을 등록합니다.")
    ApiResponse<CouponAdminV1Dto.CouponResponse> register(CouponAdminV1Dto.RegisterRequest request);

    @Operation(summary = "쿠폰 수정", description = "쿠폰 정보를 수정합니다.")
    ApiResponse<CouponAdminV1Dto.CouponResponse> update(Long couponId, CouponAdminV1Dto.UpdateRequest request);

    @Operation(summary = "쿠폰 삭제", description = "쿠폰을 삭제합니다.")
    ApiResponse<Void> delete(Long couponId);

    @Operation(summary = "쿠폰 발급 내역 조회", description = "특정 쿠폰의 발급 내역을 페이지 단위로 조회합니다.")
    ApiResponse<PageResponse<CouponAdminV1Dto.IssueResponse>> getIssues(Long couponId, Pageable pageable);
}
