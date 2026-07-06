package com.loopers.coupon.infrastructure;

import com.loopers.coupon.domain.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponJpaRepository extends JpaRepository<Coupon, Long> {

    /**
     * 원자적 수량 차감. 한도가 없거나(issued_count < quantity) 남았을 때만 issued_count 를 1 올린다.
     * 반환값(영향 행 수): 1 = 슬롯 확보, 0 = 소진. 파티션 직렬화에 의존하지 않고 그 자체로 race-free.
     */
    @Modifying
    @Query(value = """
            UPDATE coupon
            SET issued_count = issued_count + 1
            WHERE id = :couponId AND (quantity IS NULL OR issued_count < quantity)
            """, nativeQuery = true)
    int tryConsumeQuota(@Param("couponId") Long couponId);
}
