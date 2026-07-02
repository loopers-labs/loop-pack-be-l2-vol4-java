package com.loopers.coupon.application;

import com.loopers.coupon.domain.Coupon;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.MemberCoupon;
import com.loopers.member.application.MemberService;
import com.loopers.support.PageSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/** 쿠폰 유스케이스의 트랜잭션 경계이자 다도메인 조합을 담당하는 유일한 Facade. */
@RequiredArgsConstructor
@Service
@Transactional
public class CouponFacade {

    private final CouponService couponService;
    private final MemberCouponService memberCouponService;
    private final MemberService memberService;

    // ===== 사용자 =====

    public MemberCouponInfo issueCoupon(Long memberId, Long couponId) {
        memberService.get(memberId);
        MemberCoupon memberCoupon = memberCouponService.issue(memberId, couponId);
        Coupon coupon = couponService.get(couponId);
        return MemberCouponInfo.of(memberCoupon, coupon, ZonedDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<MemberCouponInfo> getMyCoupons(Long memberId) {
        memberService.get(memberId);
        List<MemberCoupon> memberCoupons = memberCouponService.getMyCoupons(memberId);
        Map<Long, Coupon> couponMap =
            couponService.getMapByIds(
                memberCoupons.stream().map(MemberCoupon::getCouponId).distinct().toList());
        ZonedDateTime now = ZonedDateTime.now();
        return memberCoupons.stream()
            .map(mc -> MemberCouponInfo.of(mc, couponMap.get(mc.getCouponId()), now))
            .toList();
    }

    // ===== 관리자(템플릿) =====

    public CouponInfo createTemplate(
        String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        return CouponInfo.from(couponService.create(name, type, value, minOrderAmount, expiredAt));
    }

    @Transactional(readOnly = true)
    public Page<CouponInfo> getTemplates(int page, int size) {
        List<CouponInfo> infos = couponService.getAll().stream().map(CouponInfo::from).toList();
        return PageSupport.paginate(infos, page, size);
    }

    @Transactional(readOnly = true)
    public CouponInfo getTemplate(Long couponId) {
        return CouponInfo.from(couponService.get(couponId));
    }

    public CouponInfo updateTemplate(
        Long couponId,
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt) {
        return CouponInfo.from(
            couponService.update(couponId, name, type, value, minOrderAmount, expiredAt));
    }

    public void deleteTemplate(Long couponId) {
        couponService.delete(couponId);
    }

    @Transactional(readOnly = true)
    public Page<MemberCouponInfo> getIssues(Long couponId, int page, int size) {
        Coupon coupon = couponService.get(couponId);
        ZonedDateTime now = ZonedDateTime.now();
        List<MemberCouponInfo> infos =
            memberCouponService.getIssuesByCoupon(couponId).stream()
                .map(mc -> MemberCouponInfo.of(mc, coupon, now))
                .toList();
        return PageSupport.paginate(infos, page, size);
    }
}
