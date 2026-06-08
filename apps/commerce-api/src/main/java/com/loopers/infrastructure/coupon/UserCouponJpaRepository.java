package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface UserCouponJpaRepository extends JpaRepository<UserCoupon, Long> {

    Optional<UserCoupon> findByOwnerUserIdAndCouponTemplateIdValue(Long userId, Long couponTemplateId);

    Page<UserCoupon> findByCouponTemplateIdValue(Long couponTemplateId, Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("""
        update UserCoupon userCoupon
        set userCoupon.status = :usedStatus,
            userCoupon.usedAt = :usedAt
        where userCoupon.id = :userCouponId
          and userCoupon.owner.userId = :userId
          and userCoupon.status = :availableStatus
        """)
    int useAvailableCoupon(
        @Param("userCouponId") Long userCouponId,
        @Param("userId") Long userId,
        @Param("usedAt") ZonedDateTime usedAt,
        @Param("availableStatus") UserCouponStatus availableStatus,
        @Param("usedStatus") UserCouponStatus usedStatus
    );
}
