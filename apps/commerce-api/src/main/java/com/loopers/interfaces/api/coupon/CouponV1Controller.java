package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.LoginUser;
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
public class CouponV1Controller {

    private final CouponFacade couponFacade;

    @PostMapping("/coupons/{couponId}/issue")
    public ApiResponse<CouponV1Dto.UserCouponResponse> issue(
        @LoginUser UserInfo loginUser,
        @PathVariable(value = "couponId") Long couponId
    ) {
        return ApiResponse.success(
            CouponV1Dto.UserCouponResponse.from(couponFacade.issueCoupon(loginUser.id(), couponId))
        );
    }

    @GetMapping("/users/me/coupons")
    public ApiResponse<List<CouponV1Dto.UserCouponResponse>> myCoupons(@LoginUser UserInfo loginUser) {
        List<CouponV1Dto.UserCouponResponse> responses = couponFacade.getMyCoupons(loginUser.id()).stream()
            .map(CouponV1Dto.UserCouponResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
