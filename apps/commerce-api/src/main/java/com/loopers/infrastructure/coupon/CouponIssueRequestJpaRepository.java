package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequest, Long> {

    Optional<CouponIssueRequest> findByRequestId(String requestId);

    List<CouponIssueRequest> findByRequestIdIn(List<String> requestIds);
}
