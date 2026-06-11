package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users/me/coupons")
public class UserCouponV1Controller {

    private final CouponFacade couponFacade;

    @GetMapping
    public ApiResponse<List<CouponV1Dto.UserCouponResponse>> getMyCoupons(@AuthenticationPrincipal Long userId) {
        List<UserCouponInfo> coupons = couponFacade.getMyCoupons(userId);
        return ApiResponse.success(coupons.stream()
            .map(CouponV1Dto.UserCouponResponse::from)
            .toList());
    }
}
