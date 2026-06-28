package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponCommand;
import com.loopers.application.coupon.CouponCommandService;
import com.loopers.application.coupon.CouponQueryService;
import com.loopers.application.coupon.CouponResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.support.HeaderValidator;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageResult;
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
public class CouponAdminController {

    private final CouponCommandService couponCommandService;
    private final CouponQueryService couponQueryService;

    @GetMapping
    public ApiResponse<PageResponse<CouponAdminDto.CouponTemplateResponse>> getCoupons(
        @RequestHeader(HeaderValidator.ADMIN_LDAP) String ldap,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        HeaderValidator.validateAdmin(ldap);
        PageResult<CouponResult.Template> result = couponQueryService.getAdminTemplates(page, size);
        return ApiResponse.success(PageResponse.from(result, CouponAdminDto.CouponTemplateResponse::from));
    }

    @GetMapping("/{couponId}")
    public ApiResponse<CouponAdminDto.CouponTemplateResponse> getCoupon(
        @RequestHeader(HeaderValidator.ADMIN_LDAP) String ldap,
        @PathVariable Long couponId
    ) {
        HeaderValidator.validateAdmin(ldap);
        return ApiResponse.success(CouponAdminDto.CouponTemplateResponse.from(
            couponQueryService.getAdminTemplate(couponId)
        ));
    }

    @PostMapping
    public ApiResponse<CouponAdminDto.CouponTemplateResponse> createCoupon(
        @RequestHeader(HeaderValidator.ADMIN_LDAP) String ldap,
        @RequestBody CouponAdminDto.UpsertCouponTemplateRequest request
    ) {
        HeaderValidator.validateAdmin(ldap);
        validateRequest(request);
        return ApiResponse.success(CouponAdminDto.CouponTemplateResponse.from(
            couponCommandService.createTemplate(toCreateCommand(request))
        ));
    }

    @PutMapping("/{couponId}")
    public ApiResponse<CouponAdminDto.CouponTemplateResponse> updateCoupon(
        @RequestHeader(HeaderValidator.ADMIN_LDAP) String ldap,
        @PathVariable Long couponId,
        @RequestBody CouponAdminDto.UpsertCouponTemplateRequest request
    ) {
        HeaderValidator.validateAdmin(ldap);
        validateRequest(request);
        return ApiResponse.success(CouponAdminDto.CouponTemplateResponse.from(
            couponCommandService.updateTemplate(couponId, toUpdateCommand(request))
        ));
    }

    @DeleteMapping("/{couponId}")
    public ApiResponse<Void> deleteCoupon(
        @RequestHeader(HeaderValidator.ADMIN_LDAP) String ldap,
        @PathVariable Long couponId
    ) {
        HeaderValidator.validateAdmin(ldap);
        couponCommandService.deleteTemplate(couponId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{couponId}/issues")
    public ApiResponse<PageResponse<CouponAdminDto.CouponIssueResponse>> getCouponIssues(
        @RequestHeader(HeaderValidator.ADMIN_LDAP) String ldap,
        @PathVariable Long couponId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        HeaderValidator.validateAdmin(ldap);
        PageResult<CouponResult.Issued> result = couponQueryService.getAdminIssues(couponId, page, size);
        return ApiResponse.success(PageResponse.from(result, CouponAdminDto.CouponIssueResponse::from));
    }

    private CouponCommand.CreateTemplate toCreateCommand(CouponAdminDto.UpsertCouponTemplateRequest request) {
        return new CouponCommand.CreateTemplate(
            request.name(),
            request.type(),
            request.value(),
            request.minOrderAmount(),
            request.totalIssueLimit(),
            request.maxIssuesPerUser(),
            request.expiredAt()
        );
    }

    private CouponCommand.UpdateTemplate toUpdateCommand(CouponAdminDto.UpsertCouponTemplateRequest request) {
        return new CouponCommand.UpdateTemplate(
            request.name(),
            request.type(),
            request.value(),
            request.minOrderAmount(),
            request.totalIssueLimit(),
            request.maxIssuesPerUser(),
            request.expiredAt()
        );
    }

    private void validateRequest(CouponAdminDto.UpsertCouponTemplateRequest request) {
        if (request == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 요청은 필수입니다.");
        }
    }
}
