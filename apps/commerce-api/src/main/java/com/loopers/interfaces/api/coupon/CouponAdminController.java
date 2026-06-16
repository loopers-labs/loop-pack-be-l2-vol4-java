package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponAdminFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api-admin/v1/coupons")
@RequiredArgsConstructor
public class CouponAdminController {

    private final CouponAdminFacade couponAdminFacade;

    @PostMapping
    public ApiResponse<CouponAdminDto.TemplateResponse> registerTemplate(
            @RequestBody CouponAdminDto.RegisterTemplateRequest request
    ) {
        return ApiResponse.success(couponAdminFacade.registerTemplate(request));
    }

    @GetMapping("/{couponId}")
    public ApiResponse<CouponAdminDto.TemplateResponse> getTemplate(
            @PathVariable("couponId") Long couponId
    ) {
        return ApiResponse.success(couponAdminFacade.getTemplate(couponId));
    }

    @GetMapping
    public ApiResponse<Page<CouponAdminDto.TemplateResponse>> getTemplates(
            Pageable pageable
    ) {
        return ApiResponse.success(couponAdminFacade.getTemplates(pageable));
    }

    @PutMapping("/{couponId}")
    public ApiResponse<CouponAdminDto.TemplateResponse> updateTemplate(
            @PathVariable("couponId") Long couponId,
            @RequestBody CouponAdminDto.UpdateTemplateRequest request
    ) {
        return ApiResponse.success(couponAdminFacade.updateTemplate(couponId, request));
    }

    @DeleteMapping("/{couponId}")
    public ApiResponse<Void> deleteTemplate(
            @PathVariable("couponId") Long couponId
    ) {
        couponAdminFacade.deleteTemplate(couponId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{couponId}/issues")
    public ApiResponse<Page<CouponAdminDto.IssueResponse>> getIssues(
            @PathVariable("couponId") Long couponId,
            Pageable pageable
    ) {
        return ApiResponse.success(couponAdminFacade.getIssues(couponId, pageable));
    }
}
