package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueResult;
import com.loopers.domain.coupon.CouponIssueResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponIssueResultRepositoryImpl implements CouponIssueResultRepository {

    private final CouponIssueResultJpaRepository jpaRepository;

    @Override
    public CouponIssueResult save(CouponIssueResult result) { return jpaRepository.save(result); }

    @Override
    public Optional<CouponIssueResult> findByRequestId(String requestId) {
        return jpaRepository.findByRequestId(requestId);
    }
}
