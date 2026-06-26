package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminV1Controller {

    private final CouponApplicationService couponApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponAdminV1Dto.TemplateDetailResponse> createTemplate(
            @Valid @RequestBody CouponAdminV1Dto.CreateTemplateRequest request
    ) {
        return ApiResponse.success(CouponAdminV1Dto.TemplateDetailResponse.from(
                couponApplicationService.createTemplate(
                        request.name(), request.type(), request.value(),
                        request.minOrderAmount(), request.expiredAt()
                )
        ));
    }

    @GetMapping
    public ApiResponse<PageResult<CouponAdminV1Dto.TemplateListResponse>> getTemplates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(PageResult.from(
                couponApplicationService.getTemplates(PageRequest.of(page, size))
                        .map(CouponAdminV1Dto.TemplateListResponse::from)
        ));
    }

    @GetMapping("/{couponTemplateId}")
    public ApiResponse<CouponAdminV1Dto.TemplateDetailResponse> getTemplate(
            @PathVariable String couponTemplateId
    ) {
        return ApiResponse.success(CouponAdminV1Dto.TemplateDetailResponse.from(
                couponApplicationService.getTemplate(couponTemplateId)
        ));
    }

    @PutMapping("/{couponTemplateId}")
    public ApiResponse<CouponAdminV1Dto.TemplateDetailResponse> updateTemplate(
            @PathVariable String couponTemplateId,
            @Valid @RequestBody CouponAdminV1Dto.UpdateTemplateRequest request
    ) {
        couponApplicationService.updateTemplate(
                couponTemplateId, request.name(), request.minOrderAmount(), request.expiredAt()
        );
        return ApiResponse.success(CouponAdminV1Dto.TemplateDetailResponse.from(
                couponApplicationService.getTemplate(couponTemplateId)
        ));
    }

    @DeleteMapping("/{couponTemplateId}")
    public ApiResponse<Object> deleteTemplate(@PathVariable String couponTemplateId) {
        couponApplicationService.deleteTemplate(couponTemplateId);
        return ApiResponse.success();
    }

    @GetMapping("/{couponTemplateId}/issues")
    public ApiResponse<PageResult<CouponAdminV1Dto.IssueHistoryResponse>> getTemplateIssues(
            @PathVariable String couponTemplateId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(PageResult.from(
                couponApplicationService.getTemplateIssues(couponTemplateId, PageRequest.of(page, size))
                        .map(CouponAdminV1Dto.IssueHistoryResponse::from)
        ));
    }
}
