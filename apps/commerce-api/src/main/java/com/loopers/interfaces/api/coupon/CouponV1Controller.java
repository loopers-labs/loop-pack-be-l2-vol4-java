package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.interceptor.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class CouponV1Controller {

    private final CouponApplicationService couponApplicationService;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    public ApiResponse<CouponV1Dto.IssueResponse> issueCoupon(
        @PathVariable Long couponId,
        HttpServletRequest request
    ) {
        UserModel user = (UserModel) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
        return ApiResponse.success(CouponV1Dto.IssueResponse.from(couponApplicationService.issueCoupon(user.getId(), couponId)));
    }

    @GetMapping("/api/v1/users/me/coupons")
    public ApiResponse<CouponV1Dto.CouponListResponse> getUserCoupons(HttpServletRequest request) {
        UserModel user = (UserModel) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
        return ApiResponse.success(CouponV1Dto.CouponListResponse.from(couponApplicationService.getUserCoupons(user.getId())));
    }
}
