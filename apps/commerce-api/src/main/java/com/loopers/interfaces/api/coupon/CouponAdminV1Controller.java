package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.admin.Admin;
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

    @GetMapping
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponPageResponse> getCoupons(
        @Admin String ldapId,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return ApiResponse.success(CouponAdminV1Dto.CouponPageResponse.from(couponFacade.getCoupons(page, size)));
    }

    @GetMapping("/{couponId}")
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> getCoupon(
        @Admin String ldapId,
        @PathVariable("couponId") Long couponId
    ) {
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(couponFacade.getCoupon(couponId)));
    }

    @PutMapping("/{couponId}")
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> update(
        @Admin String ldapId,
        @PathVariable("couponId") Long couponId,
        @RequestBody CouponAdminV1Dto.UpdateCouponRequest request
    ) {
        CouponInfo info = couponFacade.update(
            couponId,
            request.name(),
            request.type(),
            request.value(),
            request.minOrderAmount(),
            request.expiredAt()
        );
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(info));
    }

    @DeleteMapping("/{couponId}")
    @Override
    public ApiResponse<Object> delete(
        @Admin String ldapId,
        @PathVariable("couponId") Long couponId
    ) {
        couponFacade.delete(couponId);
        return ApiResponse.success();
    }

    @GetMapping("/{couponId}/issues")
    @Override
    public ApiResponse<CouponAdminV1Dto.IssuePageResponse> getIssues(
        @Admin String ldapId,
        @PathVariable("couponId") Long couponId,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return ApiResponse.success(CouponAdminV1Dto.IssuePageResponse.from(couponFacade.getIssues(couponId, page, size)));
    }
}
