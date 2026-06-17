package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {
    UserCoupon save(UserCoupon userCoupon);
    Optional<UserCoupon> find(Long id);
    Optional<UserCoupon> findWithLock(Long id);
    List<UserCoupon> findAllByMemberId(Long memberId);
    Page<UserCoupon> findAllByCouponTemplateId(Long couponTemplateId, Pageable pageable);
}
