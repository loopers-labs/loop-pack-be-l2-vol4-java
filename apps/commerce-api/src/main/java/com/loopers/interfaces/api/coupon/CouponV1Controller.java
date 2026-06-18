package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthenticatedUser;
import com.loopers.interfaces.auth.LoginUser;
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
    public ApiResponse<CouponV1Dto.Response> issue(
        @AuthenticatedUser LoginUser loginUser,
        @PathVariable("couponId") Long couponId
    ) {
        CouponV1Dto.Response response = CouponV1Dto.Response.from(couponFacade.issue(loginUser.id(), couponId));
        return ApiResponse.success(response);
    }

    @GetMapping("/users/me/coupons")
    @Override
    public ApiResponse<List<CouponV1Dto.Response>> getMyCoupons(
        @AuthenticatedUser LoginUser loginUser
    ) {
        List<CouponV1Dto.Response> responses = couponFacade.getMyCoupons(loginUser.id()).stream()
            .map(CouponV1Dto.Response::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
