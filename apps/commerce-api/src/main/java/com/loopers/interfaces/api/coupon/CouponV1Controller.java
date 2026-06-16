package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CouponV1Controller {

    private final CouponFacade couponFacade;

    @PostMapping("/coupons/{couponId}/issue")
    public ApiResponse<Void> issueCoupon(
            @RequestHeader("X-Loopers-UserId") Long userId,
            @PathVariable("couponId") Long couponId
    ) {
        couponFacade.issueCoupon(userId, couponId);
        return ApiResponse.success(null);
    }

    @GetMapping("/users/me/coupons")
    public ApiResponse<List<CouponV1Dto.UserCouponResponse>> getUsersCoupons(
            @RequestHeader("X-Loopers-UserId") Long userId
    ) {
        List<CouponV1Dto.UserCouponResponse> responses = couponFacade.getUsersCoupons(userId);
        return ApiResponse.success(responses);
    }
}
