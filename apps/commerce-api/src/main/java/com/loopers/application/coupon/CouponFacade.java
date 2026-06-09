package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateService;
import com.loopers.domain.coupon.IssuedCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponTemplateService couponTemplateService;
    private final IssuedCouponService issuedCouponService;

    public Page<IssuedCouponInfo> getIssuedCouponsByTemplateId(Long couponTemplateId, Pageable pageable) {
        CouponTemplateModel template = couponTemplateService.getById(couponTemplateId);
        return issuedCouponService.findAllByCouponTemplateId(template.getId(), pageable)
                .map(issued -> IssuedCouponInfo.from(issued, template.isExpired()));
    }
}
