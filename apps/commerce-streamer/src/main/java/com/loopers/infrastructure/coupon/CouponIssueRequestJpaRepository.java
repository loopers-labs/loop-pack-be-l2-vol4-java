package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequestModel, Long> {

    /** requestId로 상태를 갱신한다. 발급 처리 트랜잭션과 같은 트랜잭션에서 호출되어 함께 커밋/롤백된다. */
    @Modifying
    @Query(
        value = "UPDATE coupon_issue_request SET status = :status, updated_at = NOW() WHERE request_id = :requestId",
        nativeQuery = true
    )
    int updateStatus(@Param("requestId") String requestId, @Param("status") String status);
}
