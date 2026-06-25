package com.loopers.coupon.application;

import com.loopers.coupon.domain.CouponTemplate;
import com.loopers.coupon.domain.CouponService;
import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

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

    public CouponInfo updateCoupon(UpdateCouponCommand command) {
        CouponTemplate coupon = couponService.updateCoupon(
            command.couponId(),
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

    @Transactional(readOnly = true)
    public PageResult<CouponIssueInfo> getCouponIssues(Long couponId, int page, int size) {
        ZonedDateTime now = ZonedDateTime.now();
        return couponService.getCouponIssues(couponId, new PageQuery(page, size))
            .map(userCoupon -> CouponIssueInfo.from(userCoupon, now));
    }

    public void deleteCoupon(Long couponId) {
        couponService.deleteCoupon(couponId);
    }
}
