package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponService;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class CouponAdminFacade {

    private final CouponService couponService;

    public CouponInfo createCoupon(CreateCouponCommand command) {
        CouponTemplate coupon = couponService.createCoupon(
            command.name(),
            command.type(),
            command.discountValue(),
            command.minimumOrderAmount(),
            command.expiredAt()
        );
        return CouponInfo.from(coupon);
    }

    @Transactional(readOnly = true)
    public CouponInfo getCoupon(Long couponId) {
        return CouponInfo.from(couponService.getCouponTemplate(couponId));
    }

    @Transactional(readOnly = true)
    public PageResult<CouponInfo> getCoupons(int page, int size) {
        return couponService.getCoupons(new PageQuery(page, size))
            .map(CouponInfo::from);
    }
}
