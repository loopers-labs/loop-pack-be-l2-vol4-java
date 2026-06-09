package com.loopers.domain.coupon;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class IssuedCouponService {

    private final IssuedCouponRepository issuedCouponRepository;

    public Page<IssuedCouponModel> findAllByCouponTemplateId(Long couponTemplateId, Pageable pageable) {
        return issuedCouponRepository.findAllByCouponTemplateId(couponTemplateId, pageable);
    }
}
