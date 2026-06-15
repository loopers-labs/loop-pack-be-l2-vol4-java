package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.application.coupon.CouponCriteria;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.domain.common.PageResult;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminV1Controller implements CouponAdminV1ApiSpec {

    private final CouponApplicationService couponApplicationService;

    @PostMapping
    @Override
    public ApiResponse<CouponV1Dto.TemplateResponse> register(
            @RequestHeader("X-Loopers-Ldap") String adminLdap,
            @RequestBody CouponV1Dto.RegisterRequest request
    ) {
        CouponInfo.Template info = couponApplicationService.registerTemplate(
                new CouponCriteria.RegisterTemplate(
                        request.name(), request.discountType(), request.discountValue(),
                        request.minOrderAmount(), request.validDays()));
        return ApiResponse.success(CouponV1Dto.TemplateResponse.from(info));
    }

    @PutMapping("/{couponId}")
    @Override
    public ApiResponse<Void> modify(
            @RequestHeader("X-Loopers-Ldap") String adminLdap,
            @PathVariable("couponId") Long couponId,
            @RequestBody CouponV1Dto.ModifyRequest request
    ) {
        couponApplicationService.modifyTemplate(
                new CouponCriteria.ModifyTemplate(
                        couponId, request.name(), request.discountType(), request.discountValue(),
                        request.minOrderAmount(), request.validDays()));
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{couponId}")
    @Override
    public ApiResponse<Void> delete(
            @RequestHeader("X-Loopers-Ldap") String adminLdap,
            @PathVariable("couponId") Long couponId
    ) {
        couponApplicationService.deleteTemplate(couponId);
        return ApiResponse.success(null);
    }

    @GetMapping
    @Override
    public ApiResponse<CouponV1Dto.TemplatePageResponse> getCoupons(
            @RequestHeader("X-Loopers-Ldap") String adminLdap,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        PageResult<CouponInfo.Template> result = couponApplicationService.getTemplatePage(page, size);
        return ApiResponse.success(CouponV1Dto.TemplatePageResponse.from(result));
    }

    @GetMapping("/{couponId}")
    @Override
    public ApiResponse<CouponV1Dto.TemplateResponse> getCoupon(
            @RequestHeader("X-Loopers-Ldap") String adminLdap,
            @PathVariable("couponId") Long couponId
    ) {
        CouponInfo.Template info = couponApplicationService.getTemplate(couponId);
        return ApiResponse.success(CouponV1Dto.TemplateResponse.from(info));
    }

    @GetMapping("/{couponId}/issues")
    @Override
    public ApiResponse<CouponV1Dto.IssueHistoryPageResponse> getIssues(
            @RequestHeader("X-Loopers-Ldap") String adminLdap,
            @PathVariable("couponId") Long couponId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        PageResult<CouponInfo.IssueHistoryItem> result = couponApplicationService.getIssueHistory(couponId, page, size);
        return ApiResponse.success(CouponV1Dto.IssueHistoryPageResponse.from(result));
    }
}
