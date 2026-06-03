package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {
    UserCouponModel save(UserCouponModel userCoupon);
    Optional<UserCouponModel> find(Long id);

    /** 내 쿠폰 목록 — 발급분 최신순 페이지 (UC-14). */
    List<UserCouponModel> findByUserId(Long userId, int page, int size);

    /** Admin 발급 내역 — 템플릿별 페이지 (UC-16). */
    List<UserCouponModel> findByCouponId(Long couponId, int page, int size);

    /**
     * 주문 적용용 발급분 선택 — (userId, couponId)의 사용 가능한 발급분 중 가장 먼저 발급된 한 장.
     * 낙관적 락(@Version) 경로: 일반 조회 후 use()→save 시 version 충돌로 동시성 보장 (UC-20 §5-A).
     * 타 유저 소유는 userId 필터로 자연히 미조회 → NOT_FOUND (§2 격리).
     */
    Optional<UserCouponModel> findFirstAvailable(Long userId, Long couponId);

    /**
     * 위와 동일하되 비관적 락(SELECT ... FOR UPDATE)으로 행을 잠그고 조회 (UC-20 §5-B).
     * 경합 트랜잭션은 선행 커밋까지 대기한다.
     */
    Optional<UserCouponModel> findFirstAvailableForUpdate(Long userId, Long couponId);
}
