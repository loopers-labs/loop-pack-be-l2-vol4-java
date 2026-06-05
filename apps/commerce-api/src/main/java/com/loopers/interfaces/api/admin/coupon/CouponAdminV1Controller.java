package com.loopers.interfaces.api.admin.coupon;

import com.loopers.application.coupon.CouponAdminFacade;
import com.loopers.application.coupon.CouponTemplateInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.admin.coupon.dto.CouponTemplateV1Response;
import com.loopers.interfaces.api.admin.coupon.dto.CreateCouponTemplateV1Request;
import com.loopers.interfaces.api.admin.coupon.dto.IssuedCouponV1Response;
import com.loopers.interfaces.api.admin.coupon.dto.UpdateCouponTemplateV1Request;
import com.loopers.interfaces.api.auth.AdminUser;
import com.loopers.interfaces.api.auth.LdapAdmin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;

@RestController
@RequestMapping("/api-admin/v1/coupons")
@RequiredArgsConstructor
public class CouponAdminV1Controller implements CouponAdminV1ApiSpec {

    /** 어드민이 입력한 만료일시(LocalDateTime)를 해석할 기준 타임존. 호스트 환경에 의존하지 않도록 명시한다. */
    private static final ZoneId ADMIN_ZONE = ZoneId.of("Asia/Seoul");

    private final CouponAdminFacade couponAdminFacade;

    @GetMapping
    @Override
    public ApiResponse<Page<CouponTemplateV1Response>> getTemplates(@LdapAdmin AdminUser admin, Pageable pageable) {
        return ApiResponse.success(couponAdminFacade.getTemplates(pageable).map(CouponTemplateV1Response::from));
    }

    @GetMapping("/{couponId}")
    @Override
    public ApiResponse<CouponTemplateV1Response> getTemplate(@LdapAdmin AdminUser admin, @PathVariable Long couponId) {
        return ApiResponse.success(CouponTemplateV1Response.from(couponAdminFacade.getTemplate(couponId)));
    }

    @PostMapping
    @Override
    public ApiResponse<CouponTemplateV1Response> create(@LdapAdmin AdminUser admin, @Valid @RequestBody CreateCouponTemplateV1Request request) {
        CouponTemplateInfo info = couponAdminFacade.create(
            request.name(), request.type(), request.value(), request.minOrderAmount(),
            request.expiredAt().atZone(ADMIN_ZONE)
        );
        return ApiResponse.success(CouponTemplateV1Response.from(info));
    }

    @PutMapping("/{couponId}")
    @Override
    public ApiResponse<CouponTemplateV1Response> update(@LdapAdmin AdminUser admin, @PathVariable Long couponId, @Valid @RequestBody UpdateCouponTemplateV1Request request) {
        CouponTemplateInfo info = couponAdminFacade.update(
            couponId, request.name(), request.type(), request.value(), request.minOrderAmount(),
            request.expiredAt().atZone(ADMIN_ZONE)
        );
        return ApiResponse.success(CouponTemplateV1Response.from(info));
    }

    @DeleteMapping("/{couponId}")
    @Override
    public ApiResponse<Object> delete(@LdapAdmin AdminUser admin, @PathVariable Long couponId) {
        couponAdminFacade.delete(couponId);
        return ApiResponse.success();
    }

    @GetMapping("/{couponId}/issues")
    @Override
    public ApiResponse<Page<IssuedCouponV1Response>> getIssues(@LdapAdmin AdminUser admin, @PathVariable Long couponId, Pageable pageable) {
        return ApiResponse.success(couponAdminFacade.getIssues(couponId, pageable).map(IssuedCouponV1Response::from));
    }
}
