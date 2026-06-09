package com.loopers.domain.coupon;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class IssuedCouponService {

    private final IssuedCouponRepository issuedCouponRepository;

    public Page<IssuedCouponModel> findAllByCouponTemplateId(Long couponTemplateId, Pageable pageable) {
        return issuedCouponRepository.findAllByCouponTemplateId(couponTemplateId, pageable);
    }

    public List<IssuedCouponModel> getMyIssuedCoupons(Long userId) {
        return issuedCouponRepository.findAllByUserId(userId);
    }

    public IssuedCouponModel issue(Long couponTemplateId, Long userId) {
        return issuedCouponRepository.save(new IssuedCouponModel(couponTemplateId, userId));
    }
}
