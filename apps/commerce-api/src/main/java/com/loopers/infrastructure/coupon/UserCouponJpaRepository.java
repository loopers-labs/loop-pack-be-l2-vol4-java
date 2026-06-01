package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserCouponJpaRepository extends JpaRepository<UserCoupon, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        insert ignore into user_coupon (user_id, coupon_template_id, status, created_at, updated_at)
        values (:userId, :couponTemplateId, :status, current_timestamp(6), current_timestamp(6))
        """, nativeQuery = true)
    int issueOnce(
        @Param("userId") Long userId,
        @Param("couponTemplateId") Long couponTemplateId,
        @Param("status") String status
    );

    Optional<UserCoupon> findByOwnerUserIdAndCouponTemplateIdValue(Long userId, Long couponTemplateId);
}
