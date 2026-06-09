package com.loopers.application.coupon;

import java.time.ZonedDateTime;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.DiscountType;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class CouponFacade {

    private final CouponRepository couponRepository;

    public CouponCreateInfo createCoupon(
        String name,
        DiscountType discountType,
        Integer discountValue,
        Integer minOrderAmount,
        ZonedDateTime expiredAt,
        ZonedDateTime now
    ) {
        CouponModel newCoupon = CouponModel.builder()
            .rawName(name)
            .type(discountType)
            .rawValue(discountValue)
            .rawMinOrderAmount(minOrderAmount)
            .rawExpiredAt(expiredAt)
            .now(now)
            .build();

        return CouponCreateInfo.from(couponRepository.save(newCoupon));
    }

    public CouponUpdateInfo updateCoupon(
        Long couponId,
        String name,
        DiscountType discountType,
        Integer discountValue,
        Integer minOrderAmount,
        ZonedDateTime expiredAt,
        ZonedDateTime now
    ) {
        CouponModel coupon = couponRepository.getActiveById(couponId);
        coupon.update(name, discountType, discountValue, minOrderAmount, expiredAt, now);

        return CouponUpdateInfo.from(coupon);
    }

    public void deleteCoupon(Long couponId) {
        couponRepository.findActiveById(couponId).ifPresent(CouponModel::delete);
    }

    @Transactional(readOnly = true)
    public Page<CouponAdminInfo> readCoupons(int page, int size) {
        return couponRepository.findActiveByPage(page, size)
            .map(CouponAdminInfo::from);
    }

    @Transactional(readOnly = true)
    public CouponAdminInfo readCoupon(Long couponId) {
        return CouponAdminInfo.from(couponRepository.getActiveById(couponId));
    }
}
