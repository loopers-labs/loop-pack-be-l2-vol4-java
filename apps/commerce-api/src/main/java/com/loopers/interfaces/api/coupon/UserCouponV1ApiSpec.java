package com.loopers.interfaces.api.coupon;

import java.util.List;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import com.loopers.interfaces.api.auth.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User Coupon V1 API", description = "Loopers 회원 보유 쿠폰 API 입니다.")
public interface UserCouponV1ApiSpec {

    @Operation(
        summary = "내 쿠폰 목록 조회",
        description = "회원이 자신이 발급받은 쿠폰 전체를 상태(사용 가능/사용 완료/만료)와 함께 발급 시각 내림차순으로 조회한다."
    )
    ApiResponse<List<UserCouponV1Dto.MyCouponResponse>> readMyCoupons(
        @Parameter(hidden = true) @LoginUser AuthenticatedUser loginUser
    );
}
