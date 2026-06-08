package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/coupons")
public class CouponV1Controller {

    private final CouponApplicationService couponApplicationService;

    @PostMapping("/{couponId}/issue")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> issueCoupon(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @PathVariable Long couponId
    ) {
        couponApplicationService.issueCoupon(loginId, couponId);
        return ApiResponse.success(null);
    }
}
