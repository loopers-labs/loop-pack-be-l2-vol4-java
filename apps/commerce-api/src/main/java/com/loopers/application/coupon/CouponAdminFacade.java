package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateService;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponAdminFacade {

    private final CouponTemplateService couponTemplateService;
    private final UserCouponService userCouponService;

    public CouponInfo.Template create(CouponCriteria.CreateTemplate criteria) {
        CouponTemplate template = couponTemplateService.create(
            criteria.name(),
            criteria.type(),
            criteria.value(),
            criteria.minOrderAmount(),
            criteria.expiredAt()
        );
        return CouponInfo.Template.from(template);
    }

    public CouponInfo.Template get(Long couponTemplateId) {
        return CouponInfo.Template.from(couponTemplateService.get(couponTemplateId));
    }

    public List<CouponInfo.Template> list(int page, int size) {
        return couponTemplateService.list(page, size).stream()
            .map(CouponInfo.Template::from)
            .toList();
    }

    public CouponInfo.Template update(Long couponTemplateId, CouponCriteria.UpdateTemplate criteria) {
        CouponTemplate updated = couponTemplateService.update(
            couponTemplateId,
            criteria.name(),
            criteria.type(),
            criteria.value(),
            criteria.minOrderAmount(),
            criteria.expiredAt()
        );
        return CouponInfo.Template.from(updated);
    }

    public void delete(Long couponTemplateId) {
        couponTemplateService.delete(couponTemplateId);
    }

    public List<CouponInfo.MyCoupon> getIssues(Long couponTemplateId, int page, int size) {
        List<UserCoupon> issues = userCouponService.getIssues(couponTemplateId, page, size);
        return issues.stream().map(CouponInfo.MyCoupon::from).toList();
    }
}
