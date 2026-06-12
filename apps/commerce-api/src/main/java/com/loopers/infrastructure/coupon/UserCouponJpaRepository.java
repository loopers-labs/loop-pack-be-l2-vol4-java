package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCoupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserCouponJpaRepository extends JpaRepository<UserCoupon, Long> {

    List<UserCoupon> findAllByUserId(Long userId);

    List<UserCoupon> findAllByCouponTemplateId(Long couponTemplateId, Pageable pageable);

    /**
     * 비관적 락 — 본 행을 트랜잭션 종료까지 점유한다.
     * 같은 쿠폰을 동시에 사용하려는 다른 트랜잭션은 SELECT 단계에서 대기한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM UserCoupon c WHERE c.id = :id")
    Optional<UserCoupon> findByIdForUpdate(@Param("id") Long id);
}
