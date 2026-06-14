package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupon Admin V1 API", description = "Loopers 쿠폰 어드민 API 입니다.")
public interface CouponAdminV1ApiSpec {

    @Operation(
        summary = "쿠폰 템플릿 등록",
        description = "정액(FIXED)/정률(RATE) 쿠폰 템플릿을 등록합니다.",
        parameters = {
            @Parameter(name = "X-Loopers-Ldap", in = ParameterIn.HEADER, required = true, description = "LDAP 인증 ID")
        }
    )
    ApiResponse<CouponAdminV1Dto.CouponResponse> register(
        @Parameter(hidden = true) String ldapId,
        @Schema(description = "쿠폰 템플릿 등록 요청") CouponAdminV1Dto.CreateCouponRequest request
    );

    @Operation(
        summary = "쿠폰 템플릿 목록 조회",
        description = "등록된 쿠폰 템플릿을 페이지 단위로 조회합니다.",
        parameters = {
            @Parameter(name = "X-Loopers-Ldap", in = ParameterIn.HEADER, required = true, description = "LDAP 인증 ID")
        }
    )
    ApiResponse<CouponAdminV1Dto.CouponPageResponse> getCoupons(
        @Parameter(hidden = true) String ldapId,
        @Parameter(description = "페이지 번호") int page,
        @Parameter(description = "페이지 크기") int size
    );

    @Operation(
        summary = "쿠폰 템플릿 상세 조회",
        description = "쿠폰 템플릿 상세를 조회합니다.",
        parameters = {
            @Parameter(name = "X-Loopers-Ldap", in = ParameterIn.HEADER, required = true, description = "LDAP 인증 ID")
        }
    )
    ApiResponse<CouponAdminV1Dto.CouponResponse> getCoupon(
        @Parameter(hidden = true) String ldapId,
        @Parameter(description = "쿠폰 템플릿 ID") Long couponId
    );

    @Operation(
        summary = "쿠폰 템플릿 수정",
        description = "쿠폰 템플릿을 수정합니다.",
        parameters = {
            @Parameter(name = "X-Loopers-Ldap", in = ParameterIn.HEADER, required = true, description = "LDAP 인증 ID")
        }
    )
    ApiResponse<CouponAdminV1Dto.CouponResponse> update(
        @Parameter(hidden = true) String ldapId,
        @Parameter(description = "쿠폰 템플릿 ID") Long couponId,
        @Schema(description = "쿠폰 템플릿 수정 요청") CouponAdminV1Dto.UpdateCouponRequest request
    );

    @Operation(
        summary = "쿠폰 템플릿 삭제",
        description = "쿠폰 템플릿을 삭제(soft delete)합니다. 이미 발급된 쿠폰은 영향받지 않습니다.",
        parameters = {
            @Parameter(name = "X-Loopers-Ldap", in = ParameterIn.HEADER, required = true, description = "LDAP 인증 ID")
        }
    )
    ApiResponse<Object> delete(
        @Parameter(hidden = true) String ldapId,
        @Parameter(description = "쿠폰 템플릿 ID") Long couponId
    );

    @Operation(
        summary = "쿠폰 발급 내역 조회",
        description = "특정 쿠폰 템플릿의 발급 내역을 페이지 단위로 조회합니다.",
        parameters = {
            @Parameter(name = "X-Loopers-Ldap", in = ParameterIn.HEADER, required = true, description = "LDAP 인증 ID")
        }
    )
    ApiResponse<CouponAdminV1Dto.IssuePageResponse> getIssues(
        @Parameter(hidden = true) String ldapId,
        @Parameter(description = "쿠폰 템플릿 ID") Long couponId,
        @Parameter(description = "페이지 번호") int page,
        @Parameter(description = "페이지 크기") int size
    );
}
