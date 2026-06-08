package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {
    UserCoupon save(UserCoupon userCoupon);

    Optional<UserCoupon> find(Long id);

    /** 발급 중복 체크용 — (userId, couponId) 쌍은 UNIQUE 이므로 단일. */
    Optional<UserCoupon> findByUserIdAndCouponId(Long userId, Long couponId);

    /** 내 쿠폰 목록 조회. */
    List<UserCoupon> findByUserId(Long userId);

    /** Admin: 특정 쿠폰 템플릿의 발급 내역 페이징 조회. */
    List<UserCoupon> findByCouponId(Long couponId, int page, int size);
}
