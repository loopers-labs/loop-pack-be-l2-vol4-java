package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {
    UserCoupon save(UserCoupon userCoupon);
    Optional<UserCoupon> find(Long id);
    List<UserCoupon> findAllByUserId(Long userId);
    List<UserCoupon> findAllByCouponTemplateId(Long couponTemplateId, int page, int size);

    /**
     * 비관적 락(PESSIMISTIC_WRITE) 으로 쿠폰을 조회한다 — 동시 사용 방지.
     * 트랜잭션 종료까지 다른 트랜잭션은 본 행을 읽을 수 없다.
     */
    Optional<UserCoupon> findForUpdate(Long id);
}
