package com.loopers.domain.coupon;

import com.loopers.domain.coupon.enums.CouponType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponRepository couponRepository;

    @Transactional(readOnly = true)
    public CouponModel get(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + couponId + "] 쿠폰을 찾을 수 없습니다."));
    }

    @Transactional
    public CouponModel create(String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        return couponRepository.save(new CouponModel(
                name,
                new CouponDiscount(type, value, minOrderAmount),
                new CouponExpiry(expiredAt)
        ));
    }

    @Transactional(readOnly = true)
    public Page<CouponModel> getList(Pageable pageable) {
        return couponRepository.findAll(pageable);
    }

    @Transactional
    public CouponModel update(Long couponId, String name, ZonedDateTime expiredAt) {
        CouponModel coupon = get(couponId);
        if (name != null) {
            coupon.updateName(name);
        }
        if (expiredAt != null) {
            coupon.extendExpiredAt(expiredAt);
        }
        return coupon;
    }

    @Transactional
    public void delete(Long couponId) {
        CouponModel coupon = get(couponId);
        coupon.validateDeletable();
        couponRepository.delete(coupon);
    }
}
