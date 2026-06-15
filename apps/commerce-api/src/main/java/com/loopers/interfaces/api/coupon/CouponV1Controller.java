package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.domain.common.PageResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class CouponV1Controller implements CouponV1ApiSpec {

    private final CouponApplicationService couponApplicationService;

    /** 쿠폰 발급 — {couponId} 는 발급 원형(쿠폰 템플릿)의 식별자다. */
    @PostMapping("/api/v1/coupons/{couponId}/issue")
    @Override
    public ApiResponse<CouponV1Dto.IssuedResponse> issue(
            @LoginUser Long userId,
            @PathVariable("couponId") Long couponId
    ) {
        CouponInfo.Issued info = couponApplicationService.issue(userId, couponId);
        return ApiResponse.success(CouponV1Dto.IssuedResponse.from(info));
    }

    @GetMapping("/api/v1/users/me/coupons")
    @Override
    public ApiResponse<CouponV1Dto.MyCouponPageResponse> getMyCoupons(
            @LoginUser Long userId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        PageResult<CouponInfo.MyCoupon> result = couponApplicationService.getMyCoupons(userId, page, size);
        return ApiResponse.success(CouponV1Dto.MyCouponPageResponse.from(result));
    }
}
