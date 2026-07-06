package com.loopers.coupon.infrastructure;

import com.loopers.coupon.domain.CouponIssueRequest;
import com.loopers.coupon.domain.CouponIssueRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequest, Long> {

    Optional<CouponIssueRequest> findByRequestId(String requestId);

    long countByCouponIdAndStatus(Long couponId, CouponIssueRequestStatus status);
}
