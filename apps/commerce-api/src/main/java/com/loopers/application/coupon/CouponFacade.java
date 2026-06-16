package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.coupon.enums.CouponType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final UserCouponService userCouponService;
    private final CouponService couponService;

    public UserCouponInfo issue(Long couponId, Long userId) {
        return UserCouponInfo.from(userCouponService.issue(couponId, userId));
    }

    public List<UserCouponInfo> getMyList(Long userId) {
        return UserCouponInfo.from(userCouponService.getMyList(userId));
    }

    public CouponInfo create(String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        return CouponInfo.from(couponService.create(name, type, value, minOrderAmount, expiredAt));
    }

    public Page<CouponInfo> getList(Pageable pageable) {
        return couponService.getList(pageable).map(CouponInfo::from);
    }

    public CouponInfo get(Long couponId) {
        return CouponInfo.from(couponService.get(couponId));
    }

    public CouponInfo update(Long couponId, String name, ZonedDateTime expiredAt) {
        return CouponInfo.from(couponService.update(couponId, name, expiredAt));
    }

    public void delete(Long couponId) {
        couponService.delete(couponId);
    }

    public Page<UserCouponInfo> getIssues(Long couponId, Pageable pageable) {
        return userCouponService.getListByCouponId(couponId, pageable)
                .map(UserCouponInfo::from);
    }
}
