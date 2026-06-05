package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.coupon.dto.IssueCouponV1Response;
import com.loopers.interfaces.api.coupon.dto.MyCouponV1Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Coupon V1 API", description = "Loopers 쿠폰 API 입니다.")
public interface CouponV1ApiSpec {

    @Operation(
        summary = "쿠폰 발급",
        description = "쿠폰 템플릿(couponId)으로 쿠폰을 발급받습니다. 같은 템플릿을 여러 번 발급받을 수 있습니다."
    )
    ApiResponse<IssueCouponV1Response> issue(AuthUser authUser, Long couponId);

    @Operation(
        summary = "내 쿠폰 목록 조회",
        description = "발급받은 쿠폰을 상태(AVAILABLE/USED/EXPIRED)와 함께 조회합니다."
    )
    ApiResponse<List<MyCouponV1Response>> getMyCoupons(AuthUser authUser);
}
