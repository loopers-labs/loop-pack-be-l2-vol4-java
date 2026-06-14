package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.Discount;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.money.Money;
import com.loopers.application.support.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class CouponFacade {
    private final CouponService couponService;

    public CouponInfo register(String name, CouponType type, long value, BigDecimal minOrderAmount, LocalDateTime expiredAt) {
        Discount discount = new Discount(type, value);
        Money minOrder = minOrderAmount == null ? Money.ZERO : new Money(minOrderAmount);
        Coupon coupon = couponService.register(name, discount, minOrder, expiredAt);
        return CouponInfo.from(coupon);
    }

    public CouponInfo update(Long couponId, String name, CouponType type, long value, BigDecimal minOrderAmount, LocalDateTime expiredAt) {
        Discount discount = new Discount(type, value);
        Money minOrder = minOrderAmount == null ? Money.ZERO : new Money(minOrderAmount);
        Coupon coupon = couponService.update(couponId, name, discount, minOrder, expiredAt);
        return CouponInfo.from(coupon);
    }

    public void delete(Long couponId) {
        couponService.delete(couponId);
    }

    public PageResult<CouponIssueInfo> getIssues(Long couponId, int page, int size) {
        List<CouponIssueInfo> items = couponService.getIssues(couponId, page, size).stream()
            .map(CouponIssueInfo::from)
            .toList();
        return new PageResult<>(items, page, size, couponService.countIssues(couponId));
    }

    public CouponInfo getCoupon(Long couponId) {
        return CouponInfo.from(couponService.getCoupon(couponId));
    }

    public PageResult<CouponInfo> getCoupons(int page, int size) {
        List<CouponInfo> items = couponService.getCouponPage(page, size).stream()
            .map(CouponInfo::from)
            .toList();
        return new PageResult<>(items, page, size, couponService.countCoupons());
    }

    public IssuedCouponInfo issue(Long userId, Long couponId) {
        UserCoupon userCoupon = couponService.issue(userId, couponId);
        return IssuedCouponInfo.from(userCoupon);
    }

    public List<UserCouponInfo> getMyCoupons(Long userId) {
        List<UserCoupon> userCoupons = couponService.getMyCoupons(userId);
        if (userCoupons.isEmpty()) {
            return List.of();
        }

        List<Long> couponIds = userCoupons.stream()
            .map(UserCoupon::getCouponId)
            .distinct()
            .toList();
        Map<Long, Coupon> coupons = couponService.getCoupons(couponIds).stream()
            .collect(Collectors.toMap(Coupon::getId, Function.identity()));

        LocalDateTime now = LocalDateTime.now();
        return userCoupons.stream()
            .map(userCoupon -> {
                Coupon coupon = coupons.get(userCoupon.getCouponId());
                CouponStatus status = userCoupon.resolveStatus(coupon.getExpiredAt(), now);
                return UserCouponInfo.of(userCoupon, coupon, status);
            })
            .toList();
    }
}
