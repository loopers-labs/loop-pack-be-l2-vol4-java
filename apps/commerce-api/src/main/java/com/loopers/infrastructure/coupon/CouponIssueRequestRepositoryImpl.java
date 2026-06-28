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
        CouponIssueRequestJpaEntity entity = request.isNew()
            ? CouponIssueRequestJpaEntity.from(request)
            : couponIssueRequestJpaRepository.findById(request.getId())
                .orElseGet(() -> CouponIssueRequestJpaEntity.from(request));
        entity.apply(request);
        return couponIssueRequestJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<CouponIssueRequest> find(Long requestId) {
        return couponIssueRequestJpaRepository.findById(requestId).map(CouponIssueRequestJpaEntity::toDomain);
    }

    @Override
    public Optional<CouponIssueRequest> findByIdAndUserId(Long requestId, String userId) {
        return couponIssueRequestJpaRepository.findByIdAndUserId(requestId, userId)
            .map(CouponIssueRequestJpaEntity::toDomain);
    }

    @Override
    public Optional<CouponIssueRequest> findForUpdate(Long requestId) {
        return couponIssueRequestJpaRepository.findWithLockById(requestId).map(CouponIssueRequestJpaEntity::toDomain);
    }
}
