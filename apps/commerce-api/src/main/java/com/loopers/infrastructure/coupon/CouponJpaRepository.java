package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponJpaRepository extends JpaRepository<CouponModel, Long> {
    Page<CouponModel> findAllByDeletedAtIsNull(Pageable pageable);

    @Modifying
    @Query("UPDATE CouponModel c SET c.issuedQuantity = c.issuedQuantity + 1 " +
        "WHERE c.id = :couponId AND (c.totalQuantity IS NULL OR c.issuedQuantity < c.totalQuantity)")
    int incrementIssuedQuantity(@Param("couponId") Long couponId);
}
