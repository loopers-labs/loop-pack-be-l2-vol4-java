package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.admin.Admin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminV1Controller implements CouponAdminV1ApiSpec {

    private final CouponFacade couponFacade;

    @PostMapping
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> register(
        @Admin String ldapId,
        @RequestBody CouponAdminV1Dto.CreateCouponRequest request
    ) {
        CouponInfo info = couponFacade.register(
            request.name(),
            request.type(),
            request.value(),
            request.minOrderAmount(),
            request.expiredAt()
        );
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(info));
    }
}
