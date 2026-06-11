package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class CouponV1Controller implements CouponV1ApiSpec {

    private final CouponFacade couponFacade;

    @PostMapping("/coupons/{couponId}/issue")
    @Override
    public ApiResponse<CouponV1Dto.UserCouponResponse> issueCoupon(
        AuthHeaders auth,
        @PathVariable(value = "couponId") Long couponId
    ) {
        return ApiResponse.success(
            CouponV1Dto.UserCouponResponse.from(couponFacade.issueCoupon(auth.loginId(), couponId))
        );
    }

    @GetMapping("/users/me/coupons")
    @Override
    public ApiResponse<List<CouponV1Dto.UserCouponResponse>> getMyCoupons(AuthHeaders auth) {
        return ApiResponse.success(
            couponFacade.getMyCoupons(auth.loginId()).stream()
                .map(CouponV1Dto.UserCouponResponse::from)
                .toList()
        );
    }
}
