package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponStockModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponStockJpaRepository extends JpaRepository<CouponStockModel, Long> {

    /**
     * 재고가 남아있을 때만 발급 수를 1 증가시키는 원자적 조건부 UPDATE.
     * <p>
     * 조건(issued &lt; quota)과 증가(issued + 1)를 한 문장으로 실행해 check-then-act 틈을 없앤다.
     * 반환값은 <b>영향받은 행 수</b>: 1이면 슬롯 확보 성공, 0이면 이미 소진(WHERE 불충족).
     */
    @Modifying
    @Query(
        value = "UPDATE coupon_stock "
            + "SET issued = issued + 1, updated_at = NOW() "
            + "WHERE coupon_id = :couponId AND issued < quota AND deleted_at IS NULL",
        nativeQuery = true
    )
    int tryIssue(@Param("couponId") Long couponId);
}
