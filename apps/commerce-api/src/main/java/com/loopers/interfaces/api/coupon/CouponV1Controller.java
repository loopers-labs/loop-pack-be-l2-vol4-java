package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.common.interceptor.AuthInterceptor;
import com.loopers.interfaces.api.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
public class CouponV1Controller implements CouponV1ApiSpec {

    private final CouponFacade couponFacade;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    @Override
    public ApiResponse<CouponV1Dto.UserCouponResponse> issue(
        @PathVariable UUID couponId,
        @RequestAttribute(AuthInterceptor.AUTHENTICATED_USER) UserModel user
    ) {
        return ApiResponse.success(CouponV1Dto.UserCouponResponse.from(couponFacade.issue(user.getId(), couponId)));
    }

    @GetMapping("/api/v1/users/me/coupons")
    @Override
    public ApiResponse<List<CouponV1Dto.UserCouponResponse>> getMyCoupons(
        @RequestAttribute(AuthInterceptor.AUTHENTICATED_USER) UserModel user
    ) {
        List<CouponV1Dto.UserCouponResponse> coupons = couponFacade.getMyCoupons(user.getId()).stream()
            .map(CouponV1Dto.UserCouponResponse::from)
            .toList();
        return ApiResponse.success(coupons);
    }
}
