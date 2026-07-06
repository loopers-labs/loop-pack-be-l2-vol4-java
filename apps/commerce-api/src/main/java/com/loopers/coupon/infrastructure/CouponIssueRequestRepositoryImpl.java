package com.loopers.coupon.infrastructure;

import com.loopers.coupon.domain.CouponIssueRequest;
import com.loopers.coupon.domain.CouponIssueRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CouponIssueRequestRepositoryImpl implements CouponIssueRequestRepository {

    private final CouponIssueRequestJpaRepository jpaRepository;

    @Override
    public CouponIssueRequest save(CouponIssueRequest request) {
        return jpaRepository.save(request);
    }

    @Override
    public Optional<CouponIssueRequest> findByRequestId(String requestId) {
        return jpaRepository.findByRequestId(requestId);
    }
}
