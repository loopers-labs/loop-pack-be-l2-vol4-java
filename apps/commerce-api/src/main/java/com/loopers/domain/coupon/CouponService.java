package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
        CouponPolicy policy = couponPolicyRepository.findActiveById(couponPolicyId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_POLICY_NOT_FOUND, "쿠폰 정책을 찾을 수 없습니다."));
        return userCouponRepository.save(UserCoupon.issue(userId, policy, ZonedDateTime.now()));
    }

    @Transactional(readOnly = true)
    public List<UserCoupon> getMyCoupons(Long userId) {
        return userCouponRepository.findByUserId(userId);
    }

    @Transactional
    public DiscountResult apply(Long requesterId, Long userCouponId, long orderAmount) {
        if (userCouponId == null) {
            return DiscountResult.none();
        }
        long discount = use(requesterId, userCouponId, orderAmount);
        return new DiscountResult(userCouponId, discount);
    }

    @Transactional
    public long use(Long requesterId, Long userCouponId, long orderAmount) {
        UserCoupon userCoupon = userCouponRepository.findById(userCouponId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        return userCoupon.use(requesterId, orderAmount, ZonedDateTime.now());
    }

    @Transactional
    public CouponPolicy createPolicy(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        return couponPolicyRepository.save(new CouponPolicy(name, type, value, minOrderAmount, expiredAt));
    }

    @Transactional(readOnly = true)
    public CouponPolicy getPolicy(Long couponPolicyId) {
        return couponPolicyRepository.findById(couponPolicyId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_POLICY_NOT_FOUND, "쿠폰 정책을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Page<CouponPolicy> getPolicies(Pageable pageable) {
        return couponPolicyRepository.findAll(pageable);
    }

    @Transactional
    public CouponPolicy updatePolicy(Long couponPolicyId, String name, Long minOrderAmount, ZonedDateTime expiredAt) {
        CouponPolicy policy = couponPolicyRepository.findActiveById(couponPolicyId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_POLICY_NOT_FOUND, "쿠폰 정책을 찾을 수 없습니다."));
        policy.update(name, minOrderAmount, expiredAt);
        return couponPolicyRepository.save(policy);
    }

    @Transactional
    public void deletePolicy(Long couponPolicyId) {
        CouponPolicy policy = couponPolicyRepository.findById(couponPolicyId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_POLICY_NOT_FOUND, "쿠폰 정책을 찾을 수 없습니다."));
        policy.delete();
    }

    @Transactional(readOnly = true)
    public Page<UserCoupon> getIssuedCoupons(Long couponPolicyId, Pageable pageable) {
        getPolicy(couponPolicyId);
        return userCouponRepository.findByCouponPolicyId(couponPolicyId, pageable);
    }
}
