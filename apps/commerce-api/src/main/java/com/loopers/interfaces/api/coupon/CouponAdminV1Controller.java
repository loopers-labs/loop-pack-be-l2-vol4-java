package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponAdminFacade;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminV1Controller {

    private final CouponAdminFacade couponAdminFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponAdminV1Dto.CouponResponse> createCoupon(
        @Valid @RequestBody CouponAdminV1Dto.CreateCouponRequest request
    ) {
        CouponInfo coupon = couponAdminFacade.createCoupon(request.toCommand());
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(coupon));
    }
}
