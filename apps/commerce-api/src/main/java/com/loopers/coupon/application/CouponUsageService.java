package com.loopers.coupon.application;

import com.loopers.common.domain.Money;
import com.loopers.coupon.domain.CouponErrorCode;
import com.loopers.coupon.domain.UserCoupon;
import com.loopers.coupon.domain.UserCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class CouponUsageService {

    private final UserCouponRepository userCouponRepository;

    @Transactional
    public Money use(Long userCouponId, Long userId, long orderAmount) {
        UserCoupon coupon = userCouponRepository.findByIdForUpdate(userCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, CouponErrorCode.COUPON_NOT_FOUND));
        coupon.use(userId, orderAmount, ZonedDateTime.now());
        return coupon.calculateDiscount(orderAmount);
    }
}
