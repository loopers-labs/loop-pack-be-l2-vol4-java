package com.loopers.domain.coupon;

public interface CouponQuotaRepository {

    /**
     * 쿠폰 정책의 발급수를 선착순 한도 내에서 원자적으로 +1 한다.
     *
     * @return 실제로 증가된 행 수 — 1 이면 발급 확보, 0 이면 소진(또는 정책 없음).
     */
    int increaseIssued(Long couponPolicyId);
}
