package com.loopers.infrastructure.coupon;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.coupon.UserCouponModel;

public interface UserCouponJpaRepository extends JpaRepository<UserCouponModel, Long> {

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);

    List<UserCouponModel> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<UserCouponModel> findByCouponIdOrderByCreatedAtDesc(Long couponId, Pageable pageable);

    Optional<UserCouponModel> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
}
