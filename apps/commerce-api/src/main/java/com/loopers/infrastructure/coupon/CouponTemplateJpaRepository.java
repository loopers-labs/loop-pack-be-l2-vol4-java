package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplateModel, Long> {

    // 원자적 UPDATE: issuedQuantity < totalQuantity일 때만 +1. 영향 row가 1이면 예약 성공.
    // version도 함께 올려, 이 UPDATE와 동시에 진행 중인 관리자의 findById-update()-save()(낙관적 락)가
    // 이 증가분을 못 보고 그대로 덮어써 잃어버리는 lost-update를 막는다(save() 시점에 CONFLICT로 감지됨).
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CouponTemplateModel c SET c.issuedQuantity = c.issuedQuantity + 1, c.version = c.version + 1 "
            + "WHERE c.id = :id AND c.issuedQuantity < c.totalQuantity")
    int increaseIssuedQuantityIfAvailable(@Param("id") Long id);
}
