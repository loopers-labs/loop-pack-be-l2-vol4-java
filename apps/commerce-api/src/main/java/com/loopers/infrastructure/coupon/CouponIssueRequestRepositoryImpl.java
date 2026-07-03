package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponIssueRequestRepositoryImpl implements CouponIssueRequestRepository {

    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @Override
    public CouponIssueRequestModel save(CouponIssueRequestModel request) {
        return couponIssueRequestJpaRepository.save(request);
    }

    @Override
    public Optional<CouponIssueRequestModel> findByRequestId(String requestId) {
        return couponIssueRequestJpaRepository.findByRequestId(requestId);
    }
}
