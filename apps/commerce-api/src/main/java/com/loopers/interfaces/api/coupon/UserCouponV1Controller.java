package com.loopers.interfaces.api.coupon;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import com.loopers.interfaces.api.auth.LoginUser;
import com.loopers.support.utils.DateTimeUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/coupons")
public class UserCouponV1Controller implements UserCouponV1ApiSpec {

    private final CouponFacade couponFacade;
    private final DateTimeUtil dateTimeUtil;

    @Override
    @GetMapping
    public ApiResponse<List<UserCouponV1Dto.MyCouponResponse>> readMyCoupons(@LoginUser AuthenticatedUser loginUser) {
        List<UserCouponV1Dto.MyCouponResponse> myCouponResponses = couponFacade.readMyCoupons(loginUser.userId(), dateTimeUtil.now())
            .stream()
            .map(UserCouponV1Dto.MyCouponResponse::from)
            .toList();

        return ApiResponse.success(myCouponResponses);
    }
}
