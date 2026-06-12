package com.loopers.tddstudy.infrastructure.coupon;

import com.loopers.tddstudy.domain.coupon.UserCoupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface  UserCouponJpaRepository extends JpaRepository<UserCoupon, Long> {

    List<UserCoupon> findAllByUserId(Long userId);

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
    List<UserCoupon> findAllByCouponId(Long couponId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserCoupon> findWithLockById(Long id);

}
