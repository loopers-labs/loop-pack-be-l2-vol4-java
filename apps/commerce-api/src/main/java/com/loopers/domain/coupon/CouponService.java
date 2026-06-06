package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CouponService {

    private final CouponPolicyRepository couponPolicyRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public UserCoupon issue(Long userId, Long couponPolicyId) {
        CouponPolicy policy = couponPolicyRepository.findById(couponPolicyId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_POLICY_NOT_FOUND, "쿠폰 정책을 찾을 수 없습니다."));
        return userCouponRepository.save(UserCoupon.issue(userId, policy, ZonedDateTime.now()));
    }

    @Transactional(readOnly = true)
    public List<UserCoupon> getMyCoupons(Long userId) {
        return userCouponRepository.findByUserId(userId);
    }

    @Transactional
    public long use(Long requesterId, Long userCouponId, long orderAmount) {
        UserCoupon userCoupon = userCouponRepository.findById(userCouponId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        return userCoupon.use(requesterId, orderAmount, ZonedDateTime.now());
    }
}
