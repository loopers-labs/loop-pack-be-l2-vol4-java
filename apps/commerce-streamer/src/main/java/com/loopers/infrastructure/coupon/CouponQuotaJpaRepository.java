package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponQuotaJpaRepository extends JpaRepository<CouponQuota, Long> {

    /**
     * 선착순 발급수를 원자적으로 +1 한다. read-modify-write 없이 DB 한 번으로 끝나
     * 동시 소비자 환경에서도 한도 초과 발급이 생기지 않는다 — WHERE 절이 곧 "선착순 N" 불변식.
     * (참고 컨벤션: ProductMetricsJpaRepository.upsertLikeDelta 의 nativeQuery 원자적 업데이트)
     */
    @Modifying
    @Query(value = """
        UPDATE coupon_policy
        SET issued_count = issued_count + 1
        WHERE id = :couponPolicyId
            AND (max_issue_count IS NULL OR issued_count < max_issue_count)
        """, nativeQuery = true)
    int increaseIssued(@Param("couponPolicyId") Long couponPolicyId);
}
