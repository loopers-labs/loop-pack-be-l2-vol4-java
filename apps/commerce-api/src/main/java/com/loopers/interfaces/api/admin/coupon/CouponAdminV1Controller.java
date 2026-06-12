package com.loopers.interfaces.api.admin.coupon;

import com.loopers.application.coupon.CouponAdminFacade;
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

    private final CouponAdminFacade couponAdminFacade;

    @PostMapping
    @Override
    public ApiResponse<CouponAdminV1Dto.TemplateResponse> create(
            @RequestBody CouponAdminV1Dto.CreateRequest request
    ) {
        return ApiResponse.success(
                CouponAdminV1Dto.TemplateResponse.from(couponAdminFacade.create(request.toCommand())));
    }

    @GetMapping
    @Override
    public ApiResponse<List<CouponAdminV1Dto.TemplateResponse>> getTemplates(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        List<CouponAdminV1Dto.TemplateResponse> body = couponAdminFacade.getTemplates(page, size).stream()
                .map(CouponAdminV1Dto.TemplateResponse::from)
                .toList();
        return ApiResponse.success(body);
    }

    @GetMapping("/{couponId}")
    @Override
    public ApiResponse<CouponAdminV1Dto.TemplateResponse> getTemplate(@PathVariable Long couponId) {
        return ApiResponse.success(
                CouponAdminV1Dto.TemplateResponse.from(couponAdminFacade.getTemplate(couponId)));
    }

    @PutMapping("/{couponId}")
    @Override
    public ApiResponse<CouponAdminV1Dto.TemplateResponse> update(
            @PathVariable Long couponId,
            @RequestBody CouponAdminV1Dto.UpdateRequest request
    ) {
        return ApiResponse.success(
                CouponAdminV1Dto.TemplateResponse.from(couponAdminFacade.update(couponId, request.toCommand())));
    }

    @DeleteMapping("/{couponId}")
    @Override
    public ApiResponse<Object> delete(@PathVariable Long couponId) {
        couponAdminFacade.delete(couponId);
        return ApiResponse.success();
    }

    @GetMapping("/{couponId}/issues")
    @Override
    public ApiResponse<List<CouponAdminV1Dto.IssueResponse>> getIssues(
            @PathVariable Long couponId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        List<CouponAdminV1Dto.IssueResponse> body = couponAdminFacade.getIssues(couponId, page, size).stream()
                .map(CouponAdminV1Dto.IssueResponse::from)
                .toList();
        return ApiResponse.success(body);
    }
}
