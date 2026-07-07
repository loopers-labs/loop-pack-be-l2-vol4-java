package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponIssueResultJpaRepository extends JpaRepository<CouponIssueResult, Long> {
    Optional<CouponIssueResult> findByRequestId(String requestId);
}
