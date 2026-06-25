package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class CouponAdminController {

    private final CouponFacade couponFacade;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api-admin/v1/coupons")
    public ApiResponse<CouponDto.TemplateResponse> createTemplate(
        @Valid @RequestBody CouponDto.TemplateCreateRequest request
    ) {
        var template = couponFacade.createTemplate(
            request.name(), request.type(), request.value(), request.minOrderAmount(), request.expiredAt()
        );
        return ApiResponse.success(CouponDto.TemplateResponse.from(template));
    }

    @GetMapping("/api-admin/v1/coupons")
    public ApiResponse<CouponDto.TemplatePageResponse> getTemplates(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        var result = couponFacade.getTemplates(PageRequest.of(page, size));
        var templates = result.getContent().stream().map(CouponDto.TemplateResponse::from).toList();
        return ApiResponse.success(new CouponDto.TemplatePageResponse(templates, result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/api-admin/v1/coupons/{templateId}")
    public ApiResponse<CouponDto.TemplateResponse> getTemplate(@PathVariable Long templateId) {
        return ApiResponse.success(CouponDto.TemplateResponse.from(couponFacade.getTemplate(templateId)));
    }

    @PutMapping("/api-admin/v1/coupons/{templateId}")
    public ApiResponse<CouponDto.TemplateResponse> updateTemplate(
        @PathVariable Long templateId,
        @Valid @RequestBody CouponDto.TemplateUpdateRequest request
    ) {
        var template = couponFacade.updateTemplate(templateId, request.name(), request.isActive());
        return ApiResponse.success(CouponDto.TemplateResponse.from(template));
    }

    @GetMapping("/api-admin/v1/coupons/{templateId}/issues")
    public ApiResponse<CouponDto.IssuancePageResponse> getIssuances(
        @PathVariable Long templateId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        var result = couponFacade.getIssuances(templateId, PageRequest.of(page, size));
        var issuances = result.getContent().stream()
            .map(uc -> new CouponDto.IssuanceResponse(uc.getId(), uc.getMemberId()))
            .toList();
        return ApiResponse.success(new CouponDto.IssuancePageResponse(issuances, result.getTotalElements(), result.getTotalPages()));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/api-admin/v1/coupons/{templateId}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long templateId) {
        couponFacade.deleteTemplate(templateId);
        return ApiResponse.success(null);
    }
}
