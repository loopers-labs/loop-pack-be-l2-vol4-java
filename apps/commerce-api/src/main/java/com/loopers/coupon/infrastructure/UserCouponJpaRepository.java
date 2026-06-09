package com.loopers.coupon.infrastructure;

import com.loopers.coupon.domain.UserCoupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserCouponJpaRepository extends JpaRepository<UserCoupon, Long> {

    boolean existsByCouponIdAndUserId(Long couponId, Long userId);

    List<UserCoupon> findByUserIdAndDeletedAtIsNull(Long userId);

    Page<UserCoupon> findByCouponIdAndDeletedAtIsNull(Long couponId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uc FROM UserCoupon uc WHERE uc.id = :id AND uc.deletedAt IS NULL")
    Optional<UserCoupon> findByIdForUpdate(@Param("id") Long id);
}
