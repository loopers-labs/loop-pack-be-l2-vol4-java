package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCouponInfo;
import com.loopers.interfaces.api.coupon.CouponV1Dto.UserCouponResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CouponFacade {

    private final CouponService couponService;

    public CouponIssue issueCoupon(Long userId, Long couponTemplateId) {
        return couponService.issue(userId, couponTemplateId);
    }

    public List<UserCouponResponse> getUsersCoupons(Long userId) {
        List<UserCouponInfo> infos = couponService.getUsersCoupons(userId);
        return infos.stream()
                .map(info -> new UserCouponResponse(
                        info.id(),
                        info.couponTemplateId(),
                        info.name(),
                        info.type(),
                        info.value(),
                        info.minOrderAmount(),
                        info.maxDiscountAmount(),
                        info.status(),
                        info.expiredAt()
                ))
                .toList();
    }
}
