package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.IssuedCoupon;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponService couponService;

    public CouponInfo issue(Long userId, Long templateId) {
        return CouponInfo.from(couponService.issue(userId, templateId));
    }

    public List<MyCouponInfo> getMyCoupons(Long userId) {
        List<IssuedCoupon> issued = couponService.getMyCoupons(userId);
        List<Long> templateIds = issued.stream().map(IssuedCoupon::getCouponTemplateId).distinct().toList();
        Map<Long, CouponTemplate> templates = couponService.getTemplatesByIds(templateIds);
        ZonedDateTime now = ZonedDateTime.now();
        return issued.stream()
            .filter(coupon -> templates.containsKey(coupon.getCouponTemplateId()))
            .map(coupon -> MyCouponInfo.from(coupon, templates.get(coupon.getCouponTemplateId()), now))
            .toList();
    }
}
