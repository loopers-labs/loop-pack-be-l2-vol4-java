package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.MyCouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CouponV1Controller {

    private final CouponFacade couponFacade;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    public ApiResponse<CouponV1Dto.MyCouponResponse> issueCoupon(
            @LoginUser String loginId,
            @PathVariable(value = "couponId") Long couponId
    ) {
        MyCouponInfo myCouponInfo = couponFacade.issueCoupon(loginId, couponId);

        CouponV1Dto.MyCouponResponse response = CouponV1Dto.MyCouponResponse.from(myCouponInfo);
        return ApiResponse.success(response);
    }

    @GetMapping("/api/v1/users/me/coupons")
    public ApiResponse<List<CouponV1Dto.MyCouponResponse>> getMyCoupons(
            @LoginUser String loginId
    ) {
        List<CouponV1Dto.MyCouponResponse> response = couponFacade.getMyCoupons(loginId).stream()
                .map(CouponV1Dto.MyCouponResponse::from)
                .toList();
        return ApiResponse.success(response);
    }
}
