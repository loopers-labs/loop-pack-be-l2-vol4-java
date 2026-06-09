package com.loopers.coupon.interfaces.api;

import com.loopers.coupon.application.CouponIssueService;
import com.loopers.coupon.application.CouponQueryService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class CouponV1Controller implements CouponV1ApiSpec {

    private final CouponIssueService couponIssueService;
    private final CouponQueryService couponQueryService;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    @Override
    public ApiResponse<CouponV1Response.IssueDetail> issue(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long couponId
    ) {
        return ApiResponse.success(CouponV1Response.IssueDetail.from(couponIssueService.issue(userId, couponId)));
    }

    @GetMapping("/api/v1/users/me/coupons")
    @Override
    public ApiResponse<CouponV1Response.MyCoupons> getMyCoupons(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(CouponV1Response.MyCoupons.from(couponQueryService.getMyCoupons(userId)));
    }
}
