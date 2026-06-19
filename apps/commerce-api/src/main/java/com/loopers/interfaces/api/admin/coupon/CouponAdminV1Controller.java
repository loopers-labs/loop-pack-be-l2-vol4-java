package com.loopers.interfaces.api.admin.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminV1Controller implements CouponAdminV1ApiSpec {

    private final CouponFacade couponFacade;

    @GetMapping
    @Override
    public ApiResponse<List<CouponAdminV1Dto.CouponResponse>> getCoupons(
        @RequestParam(value = "page", required = false, defaultValue = "0") int page,
        @RequestParam(value = "size", required = false, defaultValue = "20") int size
    ) {
        return ApiResponse.success(couponFacade.getCoupons(page, size).stream()
            .map(CouponAdminV1Dto.CouponResponse::from)
            .toList());
    }

    @GetMapping("/{couponId}")
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> getCoupon(@PathVariable(value = "couponId") Long couponId) {
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(couponFacade.getCoupon(couponId)));
    }

    @PostMapping
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> createCoupon(
        @RequestBody CouponAdminV1Dto.CouponRequest request
    ) {
        return ApiResponse.success(
            CouponAdminV1Dto.CouponResponse.from(couponFacade.createCoupon(request.toCreateCommand()))
        );
    }

    @PutMapping("/{couponId}")
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> updateCoupon(
        @PathVariable(value = "couponId") Long couponId,
        @RequestBody CouponAdminV1Dto.CouponRequest request
    ) {
        return ApiResponse.success(
            CouponAdminV1Dto.CouponResponse.from(couponFacade.updateCoupon(couponId, request.toUpdateCommand()))
        );
    }

    @DeleteMapping("/{couponId}")
    @Override
    public ApiResponse<Object> deleteCoupon(@PathVariable(value = "couponId") Long couponId) {
        couponFacade.deleteCoupon(couponId);
        return ApiResponse.success();
    }

    @GetMapping("/{couponId}/issues")
    @Override
    public ApiResponse<List<CouponAdminV1Dto.IssuedCouponResponse>> getIssuedCoupons(
        @PathVariable(value = "couponId") Long couponId,
        @RequestParam(value = "page", required = false, defaultValue = "0") int page,
        @RequestParam(value = "size", required = false, defaultValue = "20") int size
    ) {
        return ApiResponse.success(couponFacade.getIssuedCoupons(couponId, page, size).stream()
            .map(CouponAdminV1Dto.IssuedCouponResponse::from)
            .toList());
    }
}
