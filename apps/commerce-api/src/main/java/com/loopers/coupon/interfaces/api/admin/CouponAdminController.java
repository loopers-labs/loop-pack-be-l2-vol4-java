package com.loopers.coupon.interfaces.api.admin;

import com.loopers.common.interfaces.api.AdminAuth;
import com.loopers.common.interfaces.api.ApiResponse;
import com.loopers.common.interfaces.api.PagedResponse;
import com.loopers.coupon.application.CouponFacade;
import com.loopers.coupon.application.CouponInfo;
import com.loopers.coupon.application.MemberCouponInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final CouponFacade couponFacade;

    @GetMapping
    public ApiResponse<PagedResponse<CouponResponse>> getCoupons(
        @RequestHeader(AdminAuth.LDAP_HEADER) String ldap,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size) {
        AdminAuth.verify(ldap);
        Page<CouponInfo> result = couponFacade.getTemplates(page, size);
        return ApiResponse.success(PagedResponse.from(result.map(CouponResponse::from)));
    }

    @GetMapping("/{couponId}")
    public ApiResponse<CouponResponse> getCoupon(
        @RequestHeader(AdminAuth.LDAP_HEADER) String ldap,
        @PathVariable("couponId") Long couponId) {
        AdminAuth.verify(ldap);
        return ApiResponse.success(CouponResponse.from(couponFacade.getTemplate(couponId)));
    }

    @PostMapping
    public ApiResponse<CouponResponse> createCoupon(
        @RequestHeader(AdminAuth.LDAP_HEADER) String ldap,
        @RequestBody CreateCouponRequest request) {
        AdminAuth.verify(ldap);
        CouponInfo info =
            couponFacade.createTemplate(
                request.name(),
                request.type(),
                request.value(),
                request.minOrderAmount(),
                request.expiredAt() == null ? null : request.expiredAt().atZone(SEOUL));
        return ApiResponse.success(CouponResponse.from(info));
    }

    @PutMapping("/{couponId}")
    public ApiResponse<CouponResponse> updateCoupon(
        @RequestHeader(AdminAuth.LDAP_HEADER) String ldap,
        @PathVariable("couponId") Long couponId,
        @RequestBody UpdateCouponRequest request) {
        AdminAuth.verify(ldap);
        CouponInfo info =
            couponFacade.updateTemplate(
                couponId,
                request.name(),
                request.type(),
                request.value(),
                request.minOrderAmount(),
                request.expiredAt() == null ? null : request.expiredAt().atZone(SEOUL));
        return ApiResponse.success(CouponResponse.from(info));
    }

    @DeleteMapping("/{couponId}")
    public ApiResponse<Void> deleteCoupon(
        @RequestHeader(AdminAuth.LDAP_HEADER) String ldap,
        @PathVariable("couponId") Long couponId) {
        AdminAuth.verify(ldap);
        couponFacade.deleteTemplate(couponId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{couponId}/issues")
    public ApiResponse<PagedResponse<CouponIssueResponse>> getIssues(
        @RequestHeader(AdminAuth.LDAP_HEADER) String ldap,
        @PathVariable("couponId") Long couponId,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size) {
        AdminAuth.verify(ldap);
        Page<MemberCouponInfo> result = couponFacade.getIssues(couponId, page, size);
        return ApiResponse.success(PagedResponse.from(result.map(CouponIssueResponse::from)));
    }
}
