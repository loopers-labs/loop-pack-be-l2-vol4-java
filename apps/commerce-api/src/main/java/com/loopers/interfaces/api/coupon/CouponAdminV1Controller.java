package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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

    private static final ZoneId COUPON_ZONE = ZoneId.of("Asia/Seoul");

    private final CouponFacade couponFacade;

    @PostMapping
    @Override
    public ApiResponse<CouponAdminV1Dto.Response> createPolicy(
        @RequestBody CouponAdminV1Dto.CreateRequest request
    ) {
        CouponAdminV1Dto.Response response = CouponAdminV1Dto.Response.from(
            couponFacade.createPolicy(request.name(), request.type(), request.value(), request.minOrderAmount(),
                toZoned(request.expiredAt()))
        );
        return ApiResponse.success(response);
    }

    @GetMapping
    @Override
    public ApiResponse<CouponAdminV1Dto.PageResponse> getPolicies(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(CouponAdminV1Dto.PageResponse.from(couponFacade.getPolicies(page, size)));
    }

    @GetMapping("/{couponPolicyId}")
    @Override
    public ApiResponse<CouponAdminV1Dto.Response> getPolicy(
        @PathVariable("couponPolicyId") Long couponPolicyId
    ) {
        return ApiResponse.success(CouponAdminV1Dto.Response.from(couponFacade.getPolicy(couponPolicyId)));
    }

    @PutMapping("/{couponPolicyId}")
    @Override
    public ApiResponse<CouponAdminV1Dto.Response> updatePolicy(
        @PathVariable("couponPolicyId") Long couponPolicyId,
        @RequestBody CouponAdminV1Dto.UpdateRequest request
    ) {
        CouponAdminV1Dto.Response response = CouponAdminV1Dto.Response.from(
            couponFacade.updatePolicy(couponPolicyId, request.name(), request.minOrderAmount(), toZoned(request.expiredAt()))
        );
        return ApiResponse.success(response);
    }

    private static ZonedDateTime toZoned(LocalDateTime expiredAt) {
        return expiredAt == null ? null : expiredAt.atZone(COUPON_ZONE);
    }

    @DeleteMapping("/{couponPolicyId}")
    @Override
    public ApiResponse<Void> deletePolicy(
        @PathVariable("couponPolicyId") Long couponPolicyId
    ) {
        couponFacade.deletePolicy(couponPolicyId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{couponPolicyId}/issues")
    @Override
    public ApiResponse<CouponAdminV1Dto.IssuedCouponPageResponse> getIssuedCoupons(
        @PathVariable("couponPolicyId") Long couponPolicyId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(
            CouponAdminV1Dto.IssuedCouponPageResponse.from(couponFacade.getIssuedCoupons(couponPolicyId, page, size))
        );
    }
}
