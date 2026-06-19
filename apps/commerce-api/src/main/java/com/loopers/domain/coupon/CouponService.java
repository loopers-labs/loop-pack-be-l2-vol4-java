package com.loopers.domain.coupon;

import com.loopers.domain.common.PageCriteria;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponRepository couponRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    public Coupon createCoupon(
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt
    ) {
        return couponRepository.save(new Coupon(name, type, value, minOrderAmount, expiredAt));
    }

    public Coupon getCoupon(Long id) {
        return getCouponById(id);
    }

    public List<Coupon> getCoupons(Integer page, Integer size) {
        PageCriteria pageCriteria = PageCriteria.of(page, size);
        return couponRepository.findAll(pageCriteria.page(), pageCriteria.size());
    }

    public Coupon updateCoupon(
        Long id,
        String name,
        CouponType type,
        Long value,
        Long minOrderAmount,
        ZonedDateTime expiredAt
    ) {
        Coupon coupon = getCouponById(id);
        coupon.update(name, type, value, minOrderAmount, expiredAt);
        return couponRepository.save(coupon);
    }

    public void deleteCoupon(Long id) {
        Coupon coupon = getCouponById(id);
        coupon.delete();
        couponRepository.save(coupon);
    }

    public IssuedCoupon issueCoupon(Long couponId, String userLoginId, ZonedDateTime now) {
        Coupon coupon = getCouponById(couponId);
        issuedCouponRepository.findByCouponIdAndUserLoginId(couponId, userLoginId)
            .ifPresent(issuedCoupon -> {
                throw new CoreException(ErrorType.CONFLICT, "이미 발급된 쿠폰입니다.");
            });

        return issuedCouponRepository.save(coupon.issueTo(userLoginId, now));
    }

    public List<IssuedCoupon> getIssuedCoupons(String userLoginId) {
        return issuedCouponRepository.findAllByUserLoginId(userLoginId);
    }

    public List<IssuedCoupon> getIssuedCoupons(Long couponId, Integer page, Integer size) {
        PageCriteria pageCriteria = PageCriteria.of(page, size);
        return issuedCouponRepository.findAllByCouponId(couponId, pageCriteria.page(), pageCriteria.size());
    }

    @Transactional
    public CouponUseResult useCoupon(
        String userLoginId,
        Long couponId,
        Long orderAmount,
        ZonedDateTime now
    ) {
        IssuedCoupon issuedCoupon = issuedCouponRepository.findByCouponIdAndUserLoginIdForUpdate(couponId, userLoginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[couponId = " + couponId + "] 발급 쿠폰을 찾을 수 없습니다."));

        Coupon coupon = getCouponById(issuedCoupon.getCouponId());
        Long discountAmount = coupon.calculateDiscount(orderAmount);
        issuedCoupon.use(userLoginId, now);
        issuedCouponRepository.save(issuedCoupon);
        return new CouponUseResult(issuedCoupon.getId(), coupon.getId(), discountAmount);
    }

    @Transactional
    public CouponUseResult useIssuedCoupon(
        String userLoginId,
        Long issuedCouponId,
        Long orderAmount,
        ZonedDateTime now
    ) {
        IssuedCoupon issuedCoupon = issuedCouponRepository.findForUpdate(issuedCouponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + issuedCouponId + "] 발급 쿠폰을 찾을 수 없습니다."));

        Coupon coupon = getCouponById(issuedCoupon.getCouponId());
        Long discountAmount = coupon.calculateDiscount(orderAmount);
        issuedCoupon.use(userLoginId, now);
        issuedCouponRepository.save(issuedCoupon);
        return new CouponUseResult(issuedCoupon.getId(), coupon.getId(), discountAmount);
    }

    private Coupon getCouponById(Long id) {
        return couponRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 쿠폰을 찾을 수 없습니다."));
    }
}
