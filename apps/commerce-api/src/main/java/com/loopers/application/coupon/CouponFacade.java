package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CouponFacade {

    private final CouponService couponService;
    private final UserCouponService userCouponService;
    private final UserService userService;

    // 쿠폰 등록 (admin)
    public CouponInfo createCoupon(String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt, Integer quantity) {
        return CouponInfo.from(couponService.createCoupon(name, type, value, minOrderAmount, expiredAt, quantity));
    }

    // 쿠폰 상세 조회 (admin)
    public CouponInfo getCoupon(Long couponId) {
        return CouponInfo.from(couponService.getCoupon(couponId));
    }

    // 쿠폰 목록 조회 (admin)
    public Page<CouponInfo> getCoupons(Pageable pageable) {
        return couponService.getCoupons(pageable).map(CouponInfo::from);
    }

    // 쿠폰 수정 (admin)
    public CouponInfo updateCoupon(Long couponId, String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt) {
        return CouponInfo.from(couponService.updateCoupon(couponId, name, type, value, minOrderAmount, expiredAt));
    }

    // 쿠폰 삭제 (admin)
    public void deleteCoupon(Long couponId) {
        couponService.deleteCoupon(couponId);
    }

    // 특정 쿠폰의 발급 내역 조회 (admin)
    public Page<CouponIssueInfo> getCouponIssues(Long couponId, Pageable pageable) {
        couponService.getCoupon(couponId);
        return userCouponService.getIssues(couponId, pageable).map(CouponIssueInfo::from);
    }

    // 쿠폰 발급 요청 (client)
    public MyCouponInfo issueCoupon(String loginId, Long couponId) {
        Long userId = userService.getMyInfo(loginId).getId();
        UserCouponModel userCoupon = userCouponService.issue(userId, couponId);
        CouponModel coupon = couponService.getCoupon(couponId);
        return MyCouponInfo.from(userCoupon, coupon, LocalDateTime.now());
    }

    // 내 쿠폰 목록 조회 (client)
    public List<MyCouponInfo> getMyCoupons(String loginId) {
        Long userId = userService.getMyInfo(loginId).getId();
        LocalDateTime now = LocalDateTime.now();
        return userCouponService.getMyCoupons(userId).stream()
                .map(userCoupon -> MyCouponInfo.from(userCoupon, couponService.getCoupon(userCoupon.getCouponId()), now))
                .toList();
    }
}
