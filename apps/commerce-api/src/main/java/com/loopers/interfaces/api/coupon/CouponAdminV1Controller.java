package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.IssuedCouponInfo;
import com.loopers.domain.coupon.CouponTemplateService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.user.AdminAuth;
import com.loopers.interfaces.api.user.AuthHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    private final CouponTemplateService couponTemplateService;
    private final CouponFacade couponFacade;

    @GetMapping
    @Override
    public ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>> getCoupons(
            @RequestHeader(AuthHeaders.LDAP) String ldap,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        AdminAuth.validate(ldap);
        Page<CouponInfo> infos = couponTemplateService.findAll(PageRequest.of(page, size))
                .map(CouponInfo::from);
        return ApiResponse.success(PageResponse.from(infos.map(CouponAdminV1Dto.CouponResponse::from)));
    }

    @GetMapping("/{couponId}")
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> getCoupon(
            @RequestHeader(AuthHeaders.LDAP) String ldap,
            @PathVariable Long couponId
    ) {
        AdminAuth.validate(ldap);
        CouponInfo info = CouponInfo.from(couponTemplateService.getById(couponId));
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(info));
    }

    @PostMapping
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> createCoupon(
            @RequestHeader(AuthHeaders.LDAP) String ldap,
            @RequestBody CouponAdminV1Dto.CouponCreateRequest request
    ) {
        AdminAuth.validate(ldap);
        CouponInfo info = CouponInfo.from(couponTemplateService.createTemplate(
                request.name(),
                request.type().toDomain(),
                request.value(),
                request.minOrderAmount(),
                request.expiredAt()
        ));
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(info));
    }

    @PutMapping("/{couponId}")
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> updateCoupon(
            @RequestHeader(AuthHeaders.LDAP) String ldap,
            @PathVariable Long couponId,
            @RequestBody CouponAdminV1Dto.CouponUpdateRequest request
    ) {
        AdminAuth.validate(ldap);
        CouponInfo info = CouponInfo.from(couponTemplateService.updateTemplate(
                couponId,
                request.name(),
                request.type().toDomain(),
                request.value(),
                request.minOrderAmount(),
                request.expiredAt()
        ));
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(info));
    }

    @DeleteMapping("/{couponId}")
    @Override
    public ApiResponse<Void> deleteCoupon(
            @RequestHeader(AuthHeaders.LDAP) String ldap,
            @PathVariable Long couponId
    ) {
        AdminAuth.validate(ldap);
        couponTemplateService.deleteTemplate(couponId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{couponId}/issues")
    @Override
    public ApiResponse<PageResponse<CouponAdminV1Dto.IssuedCouponResponse>> getIssuedCoupons(
            @RequestHeader(AuthHeaders.LDAP) String ldap,
            @PathVariable Long couponId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        AdminAuth.validate(ldap);
        Page<IssuedCouponInfo> infos = couponFacade.getIssuedCouponsByTemplateId(couponId, PageRequest.of(page, size));
        return ApiResponse.success(PageResponse.from(infos.map(CouponAdminV1Dto.IssuedCouponResponse::from)));
    }
}
