package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponAdminService;
import com.loopers.domain.coupon.CouponType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class CouponAdminFacade {

    private final CouponAdminService couponAdminService;

    public CouponTemplateInfo create(String name, CouponType type, long discountValue, Long minOrderAmount, ZonedDateTime expiredAt) {
        return CouponTemplateInfo.from(couponAdminService.create(name, type, discountValue, minOrderAmount, expiredAt));
    }

    public CouponTemplateInfo getTemplate(Long id) {
        return CouponTemplateInfo.from(couponAdminService.getTemplate(id));
    }

    public Page<CouponTemplateInfo> getTemplates(Pageable pageable) {
        return couponAdminService.getTemplates(pageable).map(CouponTemplateInfo::from);
    }

    public CouponTemplateInfo update(Long id, String name, CouponType type, long discountValue, Long minOrderAmount, ZonedDateTime expiredAt) {
        return CouponTemplateInfo.from(couponAdminService.update(id, name, type, discountValue, minOrderAmount, expiredAt));
    }

    public void delete(Long id) {
        couponAdminService.delete(id);
    }

    public Page<IssuedCouponInfo> getIssues(Long templateId, Pageable pageable) {
        return couponAdminService.getIssues(templateId, pageable).map(IssuedCouponInfo::from);
    }
}
