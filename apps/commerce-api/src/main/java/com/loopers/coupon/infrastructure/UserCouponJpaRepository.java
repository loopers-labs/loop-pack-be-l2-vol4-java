package com.loopers.coupon.infrastructure;

import com.loopers.coupon.domain.UserCoupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserCouponJpaRepository extends JpaRepository<UserCoupon, Long> {

    boolean existsByCouponIdAndUserId(Long couponId, Long userId);

    Optional<UserCoupon> findByIdAndDeletedAtIsNull(Long id);

    List<UserCoupon> findByUserIdAndDeletedAtIsNull(Long userId);

    Page<UserCoupon> findByCouponIdAndDeletedAtIsNull(Long couponId, Pageable pageable);
}
