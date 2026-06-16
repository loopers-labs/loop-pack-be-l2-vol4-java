package com.loopers.coupon.application;

import com.loopers.common.domain.Money;
import com.loopers.coupon.domain.CouponErrorCode;
import com.loopers.coupon.domain.UserCoupon;
import com.loopers.coupon.domain.UserCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Slf4j
@RequiredArgsConstructor
@Component
public class CouponUsageService {

    private final UserCouponRepository userCouponRepository;

    @Transactional
    public Money use(Long userCouponId, Long userId, long orderAmount) {
        UserCoupon coupon = userCouponRepository.findById(userCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, CouponErrorCode.COUPON_NOT_FOUND));
        coupon.use(userId, orderAmount, ZonedDateTime.now());
        Money discount = coupon.calculateDiscount(orderAmount);

        // 충돌 검출을 커밋이 아닌 이 자리에서 받기 위해 version UPDATE 를 즉시 flush 한다.
        try {
            userCouponRepository.flush();
        } catch (OptimisticLockingFailureException e) {
            throw new CoreException(ErrorType.CONFLICT, CouponErrorCode.COUPON_ALREADY_USED);
        }
        log.info("쿠폰 사용 userCouponId={} userId={} orderAmount={} discount={}",
                userCouponId, userId, orderAmount, discount.value());
        return discount;
    }

    @Transactional
    public void restore(Long userCouponId) {
        userCouponRepository.findById(userCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, CouponErrorCode.COUPON_NOT_FOUND))
                .restore();
        log.info("쿠폰 복원 userCouponId={}", userCouponId);
    }
}
