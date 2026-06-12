package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserCouponJpaRepository extends JpaRepository<UserCouponModel, Long> {
    Optional<UserCouponModel> findByIdAndDeletedAtIsNull(Long id);
    Optional<UserCouponModel> findByUserIdAndCouponIdAndDeletedAtIsNull(Long userId, Long couponId);
    List<UserCouponModel> findAllByUserIdAndDeletedAtIsNull(Long userId);
    boolean existsByUserIdAndCouponIdAndDeletedAtIsNull(Long userId, Long couponId);
}
