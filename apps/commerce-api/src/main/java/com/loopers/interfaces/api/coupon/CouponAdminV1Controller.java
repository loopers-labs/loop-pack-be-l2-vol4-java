package com.loopers.interfaces.api.coupon;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.coupon.CouponCreateInfo;
import com.loopers.application.coupon.CouponFacade;
import com.loopers.interfaces.api.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminV1Controller implements CouponAdminV1ApiSpec {

    private final CouponFacade couponFacade;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponAdminV1Dto.CreateResponse> createCoupon(@Valid @RequestBody CouponAdminV1Dto.CreateRequest request) {
        CouponCreateInfo couponCreateInfo = couponFacade.createCoupon(
            request.name(),
            request.discountType(),
            request.discountValue(),
            request.minOrderAmount(),
            request.expiredAt()
        );

        return ApiResponse.success(CouponAdminV1Dto.CreateResponse.from(couponCreateInfo));
    }
}
