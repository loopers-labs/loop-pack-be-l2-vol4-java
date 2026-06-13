package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponCommand;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.CouponService;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminV1Controller {

    private final CouponService couponService;

    @GetMapping
    public ApiResponse<Page<CouponAdminV1Dto.CouponResponse>> getCoupons(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<Coupon> coupons = couponService.getCoupons(PageRequest.of(page, size));
        return ApiResponse.success(coupons.map(c -> CouponAdminV1Dto.CouponResponse.from(CouponInfo.Detail.from(c))));
    }

    @GetMapping("/{couponId}")
    public ApiResponse<CouponAdminV1Dto.CouponResponse> getCoupon(
            @PathVariable Long couponId
    ) {
        Coupon coupon = couponService.getCoupon(couponId);
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(CouponInfo.Detail.from(coupon)));
    }

    @PostMapping
    public ApiResponse<CouponAdminV1Dto.CouponResponse> createCoupon(
            @RequestBody @Valid CouponAdminV1Dto.CouponCreateRequest request
    ) {
        Coupon coupon = couponService.createCoupon(
            new CouponCommand.Create(request.name(), request.type(), request.value(), request.minOrderAmount(), request.expiredAt())
        );
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(CouponInfo.Detail.from(coupon)));
    }

    @PutMapping("/{couponId}")
    public ApiResponse<CouponAdminV1Dto.CouponResponse> updateCoupon(
            @PathVariable Long couponId,
            @RequestBody @Valid CouponAdminV1Dto.CouponUpdateRequest request
    ) {
        Coupon coupon = couponService.updateCoupon(
            couponId,
            new CouponCommand.Update(request.name(), request.type(), request.value(), request.minOrderAmount(), request.expiredAt())
        );
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(CouponInfo.Detail.from(coupon)));
    }

    @DeleteMapping("/{couponId}")
    public ApiResponse<Void> deleteCoupon(
            @PathVariable Long couponId
    ) {
        couponService.deleteCoupon(couponId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{couponId}/issues")
    public ApiResponse<Page<CouponAdminV1Dto.IssuedCouponResponse>> getIssuedCoupons(
            @PathVariable Long couponId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<IssuedCoupon> issued = couponService.getIssuedCoupons(couponId, PageRequest.of(page, size));
        return ApiResponse.success(issued.map(i -> CouponAdminV1Dto.IssuedCouponResponse.from(CouponInfo.Issued.from(i))));
    }
}
