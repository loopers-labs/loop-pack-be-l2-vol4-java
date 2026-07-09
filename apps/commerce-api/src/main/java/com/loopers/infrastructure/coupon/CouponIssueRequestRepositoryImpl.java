package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponIssueRequestRepositoryImpl implements CouponIssueRequestRepository {

    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @Override
    public CouponIssueRequest save(CouponIssueRequest request) {
        return couponIssueRequestJpaRepository.save(request);
    }

    @Override
    public Optional<CouponIssueRequest> findByRequestId(String requestId) {
        return couponIssueRequestJpaRepository.findByRequestId(requestId);
    }
}
