package com.loopers.interfaces.api.coupon;

import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestAttribute;

import java.util.List;
import java.util.UUID;

@Tag(name = "Coupon V1 API", description = "대고객 쿠폰 API (발급/조회)")
public interface CouponV1ApiSpec {

    @Operation(summary = "쿠폰 발급", description = "쿠폰 템플릿(couponId)을 본인에게 발급합니다.")
    ApiResponse<CouponV1Dto.UserCouponResponse> issue(
        UUID couponId,
        @Parameter(hidden = true) @RequestAttribute(value = "authenticatedUser") UserModel user
    );

    @Operation(summary = "내 쿠폰 목록 조회", description = "본인 발급 쿠폰을 상태(AVAILABLE/USED/EXPIRED)와 함께 반환합니다.")
    ApiResponse<List<CouponV1Dto.UserCouponResponse>> getMyCoupons(
        @Parameter(hidden = true) @RequestAttribute(value = "authenticatedUser") UserModel user
    );
}
