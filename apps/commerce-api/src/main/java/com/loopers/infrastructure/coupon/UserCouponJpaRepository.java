package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserCouponJpaRepository extends JpaRepository<UserCouponModel, Long> {
    Optional<UserCouponModel> findByIdAndDeletedAtIsNull(Long id);
    Optional<UserCouponModel> findByUserIdAndCouponIdAndDeletedAtIsNull(Long userId, Long couponId);
    List<UserCouponModel> findAllByUserIdAndDeletedAtIsNull(Long userId);
    boolean existsByUserIdAndCouponIdAndDeletedAtIsNull(Long userId, Long couponId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uc FROM UserCouponModel uc WHERE uc.id = :id AND uc.deletedAt IS NULL")
    Optional<UserCouponModel> findByIdForUpdate(@Param("id") Long id);
}
