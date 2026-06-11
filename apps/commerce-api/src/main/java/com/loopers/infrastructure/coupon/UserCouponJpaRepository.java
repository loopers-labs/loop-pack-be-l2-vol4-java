package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserCouponJpaRepository extends JpaRepository<UserCouponModel, UUID> {
    Optional<UserCouponModel> findByIdAndUserId(UUID id, UUID userId);

    List<UserCouponModel> findByUserId(UUID userId);

    Page<UserCouponModel> findByTemplateId(UUID templateId, Pageable pageable);

    // 조건부 UPDATE — AVAILABLE 일 때만 USED 로 전이 (동시 이중사용 방지).
    // plain @Modifying (clearAutomatically=false): 미flush된 order 가 1LC 에서 detach 되는 것 방지
    @Modifying
    @Query("""
        UPDATE UserCouponModel u
        SET u.status = com.loopers.domain.coupon.UserCouponStatus.USED, u.orderId = :orderId, u.usedAt = :usedAt
        WHERE u.id = :id AND u.status = com.loopers.domain.coupon.UserCouponStatus.AVAILABLE
        """)
    int useIfAvailable(@Param("id") UUID id, @Param("orderId") UUID orderId, @Param("usedAt") ZonedDateTime usedAt);

    @Modifying
    @Query("""
        UPDATE UserCouponModel u
        SET u.status = com.loopers.domain.coupon.UserCouponStatus.AVAILABLE, u.orderId = null, u.usedAt = null
        WHERE u.orderId = :orderId AND u.status = com.loopers.domain.coupon.UserCouponStatus.USED
        """)
    int releaseByOrderId(@Param("orderId") UUID orderId);

    @Modifying
    @Query("""
        UPDATE UserCouponModel u
        SET u.status = com.loopers.domain.coupon.UserCouponStatus.AVAILABLE, u.orderId = null, u.usedAt = null
        WHERE u.orderId IN :orderIds AND u.status = com.loopers.domain.coupon.UserCouponStatus.USED
        """)
    int releaseByOrderIds(@Param("orderIds") List<UUID> orderIds);
}
