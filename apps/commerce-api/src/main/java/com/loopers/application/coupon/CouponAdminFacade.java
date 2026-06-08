package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
}
