package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.UserCouponService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class CouponController {

    private final CouponFacade couponFacade;
    private final UserCouponService userCouponService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/v1/coupons/{templateId}/issue")
    public ApiResponse<CouponDto.IssueResponse> issue(
        @PathVariable Long templateId,
        @RequestAttribute("userId") Long userId
    ) {
        var userCoupon = couponFacade.issue(userId, templateId);
        return ApiResponse.success(CouponDto.IssueResponse.from(userCoupon.getId()));
    }

    @GetMapping("/api/v1/users/me/coupons")
    public ApiResponse<List<CouponDto.MyCouponResponse>> getMyCoupons(
        @RequestAttribute("userId") Long userId
    ) {
        var coupons = userCouponService.getMyCoupons(userId).stream()
            .map(CouponDto.MyCouponResponse::from)
            .toList();
        return ApiResponse.success(coupons);
    }
}
