package com.loopers.coupon.domain;

import java.util.List;
import java.util.Optional;

public interface MemberCouponRepository {

    MemberCoupon save(MemberCoupon memberCoupon);

    Optional<MemberCoupon> find(Long id);

    /** 비관적 쓰기 락으로 회원 쿠폰을 조회한다. 동일 쿠폰 동시 사용을 직렬화한다. */
    Optional<MemberCoupon> findByIdForUpdate(Long id);

    List<MemberCoupon> findByMemberId(Long memberId);

    List<MemberCoupon> findByCouponId(Long couponId);
}
