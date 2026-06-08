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
}
