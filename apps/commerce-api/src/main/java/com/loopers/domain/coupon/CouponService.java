package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

    @Transactional
    public CouponModel createCoupon(String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt, Integer quantity) {
        CouponModel coupon = CouponModel.of(name, DiscountPolicy.of(type, value), minOrderAmount, expiredAt, quantity);
        return couponRepository.save(coupon);
    }

    @Transactional(readOnly = true)
    public CouponModel getCoupon(Long couponId) {
        return couponRepository.find(couponId)
                .orElseThrow(
                    () -> new CoreException(ErrorType.NOT_FOUND, "[couponId = " + couponId + "] 쿠폰을 찾을 수 없습니다.")
                );
    }

    @Transactional(readOnly = true)
    public Page<CouponModel> getCoupons(Pageable pageable) {
        return couponRepository.search(pageable);
    }

    @Transactional
    public CouponModel updateCoupon(Long couponId, String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt) {
        CouponModel coupon = getCoupon(couponId);
        coupon.update(name, DiscountPolicy.of(type, value), minOrderAmount, expiredAt);
        return coupon;
    }

    @Transactional
    public void decreaseQuantity(Long couponId) {
        CouponModel coupon = couponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        coupon.decreaseQuantity();
    }

    @Transactional
    public void deleteCoupon(Long couponId) {
        CouponModel coupon = getCoupon(couponId);
        coupon.delete();
        couponRepository.save(coupon);
    }
}