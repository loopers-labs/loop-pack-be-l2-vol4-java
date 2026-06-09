package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import com.loopers.interfaces.api.auth.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupon V1 API", description = "Loopers 쿠폰 도메인 회원 API 입니다.")
public interface CouponV1ApiSpec {

    @Operation(
        summary = "쿠폰 발급",
        description = "회원이 특정 쿠폰 템플릿으로부터 자신의 쿠폰 한 장을 발급받는다. 발급 시점 템플릿 정보를 스냅샷으로 기록하며, 한 템플릿에서 한 장만 발급받을 수 있다."
    )
    ApiResponse<CouponV1Dto.IssueResponse> issueCoupon(
        Long couponId,
        @Parameter(hidden = true) @LoginUser AuthenticatedUser loginUser
    );
}
