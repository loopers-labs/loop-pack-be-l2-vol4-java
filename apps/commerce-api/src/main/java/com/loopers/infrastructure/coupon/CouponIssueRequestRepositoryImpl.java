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
        CouponIssueRequestJpaEntity entity = couponIssueRequestJpaRepository.findByRequestIdAndDeletedAtIsNull(request.getRequestId())
            .map(existing -> {
                existing.update(request);
                return existing;
            })
            .orElseGet(() -> CouponIssueRequestJpaEntity.from(request));
        return couponIssueRequestJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<CouponIssueRequest> findByRequestId(String requestId) {
        return couponIssueRequestJpaRepository.findByRequestIdAndDeletedAtIsNull(requestId)
            .map(CouponIssueRequestJpaEntity::toDomain);
    }
}
