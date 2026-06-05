package com.loopers.interfaces.api.admin.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.admin.coupon.dto.CouponTemplateV1Response;
import com.loopers.interfaces.api.admin.coupon.dto.CreateCouponTemplateV1Request;
import com.loopers.interfaces.api.admin.coupon.dto.IssuedCouponV1Response;
import com.loopers.interfaces.api.admin.coupon.dto.UpdateCouponTemplateV1Request;
import com.loopers.interfaces.api.auth.AdminUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Tag(name = "Coupon Admin V1 API", description = "Loopers 쿠폰 어드민 API 입니다. X-Loopers-Ldap 헤더로 인증합니다.")
public interface CouponAdminV1ApiSpec {

    @Operation(summary = "쿠폰 템플릿 목록 조회", description = "쿠폰 템플릿을 페이징하여 조회합니다.")
    ApiResponse<Page<CouponTemplateV1Response>> getTemplates(AdminUser admin, Pageable pageable);

    @Operation(summary = "쿠폰 템플릿 상세 조회", description = "쿠폰 템플릿 단건을 조회합니다.")
    ApiResponse<CouponTemplateV1Response> getTemplate(AdminUser admin, Long couponId);

    @Operation(summary = "쿠폰 템플릿 등록", description = "정액(FIXED)/정률(RATE) 쿠폰 템플릿을 등록합니다.")
    ApiResponse<CouponTemplateV1Response> create(AdminUser admin, CreateCouponTemplateV1Request request);

    @Operation(summary = "쿠폰 템플릿 수정", description = "쿠폰 템플릿 내용을 수정합니다.")
    ApiResponse<CouponTemplateV1Response> update(AdminUser admin, Long couponId, UpdateCouponTemplateV1Request request);

    @Operation(summary = "쿠폰 템플릿 삭제", description = "쿠폰 템플릿을 삭제합니다.")
    ApiResponse<Object> delete(AdminUser admin, Long couponId);

    @Operation(summary = "발급 내역 조회", description = "특정 쿠폰 템플릿의 발급 내역을 페이징하여 조회합니다.")
    ApiResponse<Page<IssuedCouponV1Response>> getIssues(AdminUser admin, Long couponId, Pageable pageable);
}
