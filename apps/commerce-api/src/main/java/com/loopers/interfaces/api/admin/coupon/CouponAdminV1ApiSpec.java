package com.loopers.interfaces.api.admin.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Coupon Admin V1 API", description = "쿠폰 템플릿 관리 API")
public interface CouponAdminV1ApiSpec {

    @Operation(summary = "쿠폰 템플릿 등록", description = "정액(FIXED)/정률(RATE) 쿠폰 템플릿을 등록한다.")
    ApiResponse<CouponAdminV1Dto.TemplateResponse> create(CouponAdminV1Dto.CreateRequest request);

    @Operation(summary = "쿠폰 템플릿 목록", description = "등록된 쿠폰 템플릿을 페이지 단위로 조회한다.")
    ApiResponse<List<CouponAdminV1Dto.TemplateResponse>> getTemplates(int page, int size);

    @Operation(summary = "쿠폰 템플릿 상세", description = "쿠폰 템플릿 단건을 조회한다.")
    ApiResponse<CouponAdminV1Dto.TemplateResponse> getTemplate(Long couponId);

    @Operation(summary = "쿠폰 템플릿 수정", description = "쿠폰 템플릿을 수정한다.")
    ApiResponse<CouponAdminV1Dto.TemplateResponse> update(Long couponId, CouponAdminV1Dto.UpdateRequest request);

    @Operation(summary = "쿠폰 템플릿 삭제", description = "쿠폰 템플릿을 soft delete 한다.")
    ApiResponse<Object> delete(Long couponId);

    @Operation(summary = "쿠폰 발급 내역", description = "특정 템플릿으로 발급된 쿠폰 내역을 조회한다.")
    ApiResponse<List<CouponAdminV1Dto.IssueResponse>> getIssues(Long couponId, int page, int size);
}
