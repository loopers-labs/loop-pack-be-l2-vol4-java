package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.domain.coupon.model.CouponTemplate;
import com.loopers.domain.coupon.model.IssuedCoupon;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminV1Controller {

    private final CouponApplicationService couponApplicationService;

    @GetMapping
    public ApiResponse<Page<CouponV1Dto.TemplateResponse>> getTemplates(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<CouponTemplate> templates = couponApplicationService.getTemplates(PageRequest.of(page, size));
        return ApiResponse.success(templates.map(CouponV1Dto.TemplateResponse::from));
    }

    @GetMapping("/{couponId}")
    public ApiResponse<CouponV1Dto.TemplateResponse> getTemplate(@PathVariable Long couponId) {
        CouponTemplate template = couponApplicationService.getTemplate(couponId);
        return ApiResponse.success(CouponV1Dto.TemplateResponse.from(template));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponV1Dto.TemplateResponse> createTemplate(
        @RequestBody CouponV1Dto.TemplateCreateRequest request
    ) {
        CouponTemplate template = couponApplicationService.createTemplate(
            request.name(), request.type(), request.value(), request.minOrderAmount(), request.expiredAt()
        );
        return ApiResponse.success(CouponV1Dto.TemplateResponse.from(template));
    }

    @PutMapping("/{couponId}")
    public ApiResponse<CouponV1Dto.TemplateResponse> updateTemplate(
        @PathVariable Long couponId,
        @RequestBody CouponV1Dto.TemplateUpdateRequest request
    ) {
        CouponTemplate template = couponApplicationService.updateTemplate(
            couponId, request.name(), request.type(), request.value(), request.minOrderAmount(), request.expiredAt()
        );
        return ApiResponse.success(CouponV1Dto.TemplateResponse.from(template));
    }

    @DeleteMapping("/{couponId}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long couponId) {
        couponApplicationService.deleteTemplate(couponId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{couponId}/issues")
    public ApiResponse<Page<CouponV1Dto.IssuedCouponResponse>> getIssuedCoupons(
        @PathVariable Long couponId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<IssuedCoupon> issues = couponApplicationService.getIssuedCoupons(couponId, PageRequest.of(page, size));
        return ApiResponse.success(issues.map(CouponV1Dto.IssuedCouponResponse::from));
    }
}
