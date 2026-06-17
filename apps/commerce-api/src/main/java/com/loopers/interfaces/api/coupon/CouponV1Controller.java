package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class CouponV1Controller {

    private final CouponService couponService;

    @PostMapping("/coupons/{couponId}/issue")
    public ApiResponse<CouponV1Dto.IssueCouponResponse> issueCoupon(
            @RequestHeader("X-USER-ID") Long memberId,
            @PathVariable Long couponId
    ) {
        UserCoupon userCoupon = couponService.issueCoupon(memberId, couponId);
        return ApiResponse.success(CouponV1Dto.IssueCouponResponse.from(userCoupon));
    }

    @GetMapping("/users/me/coupons")
    public ApiResponse<List<CouponV1Dto.UserCouponResponse>> getMyCoupons(
            @RequestHeader("X-USER-ID") Long memberId
    ) {
        List<CouponV1Dto.UserCouponResponse> responses = couponService.getMyCoupons(memberId).stream()
                .map(CouponV1Dto.UserCouponResponse::from)
                .toList();
        return ApiResponse.success(responses);
    }
}
