package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
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

    @Override
    public List<CouponIssueRequest> findByRequestIdIn(List<String> requestIds) {
        if (requestIds.isEmpty()) {
            return List.of();
        }
        return couponIssueRequestJpaRepository.findByRequestIdIn(requestIds);
    }
}
