package com.loopers.infrastructure.coupon;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCouponJpaEntity, Long> {
    Optional<IssuedCouponJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IssuedCouponJpaEntity> findWithLockByIdAndDeletedAtIsNull(Long id);

    Optional<IssuedCouponJpaEntity> findByCouponIdAndUserLoginIdAndDeletedAtIsNull(Long couponId, String userLoginId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IssuedCouponJpaEntity> findWithLockByCouponIdAndUserLoginIdAndDeletedAtIsNull(Long couponId, String userLoginId);

    List<IssuedCouponJpaEntity> findAllByUserLoginIdAndDeletedAtIsNull(String userLoginId);

    List<IssuedCouponJpaEntity> findAllByCouponIdAndDeletedAtIsNull(Long couponId, Pageable pageable);
}
