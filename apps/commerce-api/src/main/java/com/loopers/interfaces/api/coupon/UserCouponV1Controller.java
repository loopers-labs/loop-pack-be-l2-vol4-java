package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.application.coupon.MyCouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserCouponV1Controller {

    private final CouponApplicationService couponApplicationService;

    @GetMapping("/me/coupons")
    public ApiResponse<List<CouponV1Dto.MyCouponResponse>> getMyCoupons(
        @RequestHeader("X-Loopers-LoginId") String loginId
    ) {
        List<MyCouponInfo> coupons = couponApplicationService.getMyCoupons(loginId);
        return ApiResponse.success(coupons.stream().map(CouponV1Dto.MyCouponResponse::from).toList());
    }
}
