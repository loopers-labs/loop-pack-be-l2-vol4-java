package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupon Admin V1 API", description = "Loopers 쿠폰 어드민 API 입니다.")
public interface CouponAdminV1ApiSpec {

    @Operation(
            summary = "쿠폰 템플릿 등록",
            description = "할인 종류(FIXED/RATE)·할인 값·유효일수를 갖춘 쿠폰 템플릿을 등록합니다."
    )
    ApiResponse<CouponV1Dto.TemplateResponse> register(String adminLdap, CouponV1Dto.RegisterRequest request);

    @Operation(
            summary = "쿠폰 템플릿 수정",
            description = "템플릿을 수정합니다. 이미 발급된 쿠폰에는 영향을 주지 않습니다(발급 시점 스냅샷)."
    )
    ApiResponse<Void> modify(String adminLdap, Long couponId, CouponV1Dto.ModifyRequest request);

    @Operation(
            summary = "쿠폰 템플릿 삭제",
            description = "템플릿을 논리 삭제합니다. 이미 발급된 쿠폰은 영향을 받지 않습니다."
    )
    ApiResponse<Void> delete(String adminLdap, Long couponId);

    @Operation(
            summary = "쿠폰 템플릿 목록 조회",
            description = "등록된 쿠폰 템플릿을 페이징하여 반환합니다."
    )
    ApiResponse<CouponV1Dto.TemplatePageResponse> getCoupons(String adminLdap, int page, int size);

    @Operation(
            summary = "쿠폰 템플릿 단건 조회",
            description = "쿠폰 템플릿 단건을 반환합니다."
    )
    ApiResponse<CouponV1Dto.TemplateResponse> getCoupon(String adminLdap, Long couponId);

    @Operation(
            summary = "쿠폰 발급 내역 조회",
            description = "특정 템플릿으로 발급된 쿠폰(발급자·상태·만료일)을 페이징하여 반환합니다."
    )
    ApiResponse<CouponV1Dto.IssueHistoryPageResponse> getIssues(String adminLdap, Long couponId, int page, int size);
}
