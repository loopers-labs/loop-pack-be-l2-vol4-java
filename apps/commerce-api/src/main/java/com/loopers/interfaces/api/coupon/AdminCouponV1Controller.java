package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class AdminCouponV1Controller {

    private final CouponService couponService;

    @GetMapping
    public ApiResponse<List<CouponV1Dto.CouponTemplateResponse>> getCoupons() {
        List<CouponV1Dto.CouponTemplateResponse> responses = couponService.getAllTemplates().stream()
                .map(CouponV1Dto.CouponTemplateResponse::from)
                .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{couponId}")
    public ApiResponse<CouponV1Dto.CouponTemplateResponse> getCoupon(@PathVariable Long couponId) {
        CouponTemplate template = couponService.getTemplate(couponId);
        return ApiResponse.success(CouponV1Dto.CouponTemplateResponse.from(template));
    }

    @PostMapping
    public ApiResponse<CouponV1Dto.CouponTemplateResponse> createCoupon(
            @RequestBody CouponV1Dto.CreateCouponTemplateRequest request
    ) {
        CouponTemplate template = couponService.createTemplate(
                request.name(), request.type(), request.value(), request.minOrderAmount(), request.expiredAt()
        );
        return ApiResponse.success(CouponV1Dto.CouponTemplateResponse.from(template));
    }

    @PutMapping("/{couponId}")
    public ApiResponse<CouponV1Dto.CouponTemplateResponse> updateCoupon(
            @PathVariable Long couponId,
            @RequestBody CouponV1Dto.UpdateCouponTemplateRequest request
    ) {
        CouponTemplate template = couponService.updateTemplate(
                couponId, request.name(), request.type(), request.value(), request.minOrderAmount(), request.expiredAt()
        );
        return ApiResponse.success(CouponV1Dto.CouponTemplateResponse.from(template));
    }

    @GetMapping("/{couponId}/issues")
    public ApiResponse<Page<CouponV1Dto.CouponIssueResponse>> getIssuedCoupons(
            @PathVariable Long couponId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<CouponV1Dto.CouponIssueResponse> result = couponService
                .getIssuedCoupons(couponId, PageRequest.of(page, size))
                .map(CouponV1Dto.CouponIssueResponse::from);
        return ApiResponse.success(result);
    }

    @DeleteMapping("/{couponId}")
    public ApiResponse<Void> deleteCoupon(@PathVariable Long couponId) {
        couponService.deleteTemplate(couponId);
        return ApiResponse.success(null);
    }
}
