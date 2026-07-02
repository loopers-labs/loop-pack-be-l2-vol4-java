package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponJpaRepository extends JpaRepository<Coupon, Long> {

    // 원자적 조건부 차감: 남은 수량 있을 때만 issued_count++ (초과발급 0 보장)
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE coupon SET issued_count = issued_count + 1
        WHERE id = :couponId AND quantity IS NOT NULL AND issued_count < quantity
        """, nativeQuery = true)
    int tryConsumeQuantity(@Param("couponId") Long couponId);
}
