package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserCouponJpaRepository extends JpaRepository<UserCouponModel, Long> {

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
    boolean existsByCouponId(Long couponId);

    @Query("SELECT uc FROM UserCouponModel uc WHERE uc.userId = :userId ORDER BY uc.createdAt DESC")
    List<UserCouponModel> findByUserId(@Param("userId") Long userId);

    @Query("SELECT uc FROM UserCouponModel uc WHERE uc.couponId = :couponId ORDER BY uc.createdAt DESC")
    List<UserCouponModel> findByCouponId(@Param("couponId") Long couponId, Pageable pageable);
}
