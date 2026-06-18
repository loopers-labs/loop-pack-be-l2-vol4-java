package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminV1Controller {

    private final CouponApplicationService couponApplicationService;

    @PostMapping
    public ApiResponse<CouponAdminV1Dto.CouponTemplateResponse> createCoupon(
        @RequestBody @Valid CouponAdminV1Dto.CreateCouponRequest request
    ) {
        return ApiResponse.success(CouponAdminV1Dto.CouponTemplateResponse.from(
            couponApplicationService.createCoupon(request.name(), request.type().toDomain(), request.value(), request.minOrderAmount(), request.expiredAt())
        ));
    }

    @GetMapping
    public ApiResponse<Page<CouponAdminV1Dto.CouponTemplateResponse>> getCoupons(
        @RequestParam(defaultValue = "0") @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
        @RequestParam(defaultValue = "20") @Min(value = 1, message = "size는 1 이상이어야 합니다.") @Max(value = 100, message = "size는 100 이하여야 합니다.") int size
    ) {
        Page<CouponAdminV1Dto.CouponTemplateResponse> result = couponApplicationService.getCoupons(PageRequest.of(page, size))
            .map(CouponAdminV1Dto.CouponTemplateResponse::from);
        return ApiResponse.success(result);
    }

    @GetMapping("/{couponId}")
    public ApiResponse<CouponAdminV1Dto.CouponTemplateResponse> getCoupon(
        @PathVariable @Min(1) Long couponId
    ) {
        return ApiResponse.success(CouponAdminV1Dto.CouponTemplateResponse.from(couponApplicationService.getCoupon(couponId)));
    }

    @PutMapping("/{couponId}")
    public ApiResponse<CouponAdminV1Dto.CouponTemplateResponse> updateCoupon(
        @PathVariable @Min(1) Long couponId,
        @RequestBody @Valid CouponAdminV1Dto.UpdateCouponRequest request
    ) {
        return ApiResponse.success(CouponAdminV1Dto.CouponTemplateResponse.from(
            couponApplicationService.updateCoupon(couponId, request.name(), request.type().toDomain(), request.value(), request.minOrderAmount(), request.expiredAt())
        ));
    }

    @DeleteMapping("/{couponId}")
    public ApiResponse<Void> deleteCoupon(@PathVariable @Min(1) Long couponId) {
        couponApplicationService.deleteCoupon(couponId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{couponId}/issues")
    public ApiResponse<Page<CouponAdminV1Dto.CouponIssueResponse>> getCouponIssues(
        @PathVariable @Min(1) Long couponId,
        @RequestParam(defaultValue = "0") @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
        @RequestParam(defaultValue = "20") @Min(value = 1, message = "size는 1 이상이어야 합니다.") @Max(value = 100, message = "size는 100 이하여야 합니다.") int size
    ) {
        return ApiResponse.success(
            couponApplicationService.getCouponIssues(couponId, PageRequest.of(page, size))
                .map(CouponAdminV1Dto.CouponIssueResponse::from)
        );
    }
}
