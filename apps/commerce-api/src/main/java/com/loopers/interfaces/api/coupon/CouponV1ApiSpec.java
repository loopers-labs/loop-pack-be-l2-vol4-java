package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Coupon API", description = "쿠폰 API")
public interface CouponV1ApiSpec {

    @Operation(
        summary = "쿠폰 발급",
        description = "로그인한 회원이 쿠폰 정책으로부터 사용자 쿠폰을 발급받습니다. 만료된 정책은 발급할 수 없습니다."
    )
    ApiResponse<CouponV1Dto.Response> issue(LoginUser loginUser, Long couponId);

    @Operation(
        summary = "내 쿠폰 목록 조회",
        description = "로그인한 회원이 보유한 쿠폰 목록을 조회합니다. 표시 상태(AVAILABLE/USED/EXPIRED)는 조회 시점에 파생됩니다."
    )
    ApiResponse<List<CouponV1Dto.Response>> getMyCoupons(LoginUser loginUser);
}
