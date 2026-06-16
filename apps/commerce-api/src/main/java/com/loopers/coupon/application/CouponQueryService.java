package com.loopers.coupon.application;

import com.loopers.coupon.domain.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class CouponQueryService {

    private final UserCouponRepository userCouponRepository;

    @Transactional(readOnly = true)
    public CouponResult.MyCoupons getMyCoupons(Long userId) {
        return CouponResult.MyCoupons.from(userCouponRepository.findByUserId(userId), ZonedDateTime.now());
    }
}
