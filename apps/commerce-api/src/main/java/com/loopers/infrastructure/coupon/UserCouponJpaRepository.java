package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCoupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface UserCouponJpaRepository extends JpaRepository<UserCoupon, Long> {
    Optional<UserCoupon> findByIdAndDeletedAtIsNull(Long id);
    List<UserCoupon> findAllByMemberIdAndDeletedAtIsNull(Long memberId);
    Page<UserCoupon> findAllByCouponTemplateIdAndDeletedAtIsNull(Long couponTemplateId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uc FROM UserCoupon uc WHERE uc.id = :id AND uc.deletedAt IS NULL")
    Optional<UserCoupon> findByIdWithLock(@Param("id") Long id);
}
