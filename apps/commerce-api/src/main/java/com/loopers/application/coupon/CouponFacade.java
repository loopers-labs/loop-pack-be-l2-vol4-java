package com.loopers.application.coupon;

import java.time.ZonedDateTime;

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
        ZonedDateTime expiredAt
    ) {
        CouponModel newCoupon = CouponModel.builder()
            .rawName(name)
            .type(discountType)
            .rawValue(discountValue)
            .rawMinOrderAmount(minOrderAmount)
            .rawExpiredAt(expiredAt)
            .build();

        return CouponCreateInfo.from(couponRepository.save(newCoupon));
    }
}
