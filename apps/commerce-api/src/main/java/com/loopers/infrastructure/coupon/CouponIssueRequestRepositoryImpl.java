package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 순수 도메인 ↔ JPA 경계. 발급 요청 PK는 앱 생성 TSID라 신규에도 id가 채워져 있으므로, id 유무 대신 실제 영속 여부로 분기한다.
 * commerce-api는 요청을 접수(INSERT)만 하고 이후 상태 전이는 commerce-streamer가 담당하므로, 이미 존재하면 그대로 반환한다.
 */
@Component
@RequiredArgsConstructor
public class CouponIssueRequestRepositoryImpl implements CouponIssueRequestRepository {

    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @Override
    public CouponIssueRequestModel save(CouponIssueRequestModel request) {
        return couponIssueRequestJpaRepository.findById(request.getId())
                .map(CouponIssueRequestEntityMapper::toDomain)
                .orElseGet(() -> CouponIssueRequestEntityMapper.toDomain(
                        couponIssueRequestJpaRepository.save(CouponIssueRequestEntityMapper.toEntity(request))));
    }

    @Override
    public Optional<CouponIssueRequestModel> find(Long requestId) {
        return couponIssueRequestJpaRepository.findById(requestId).map(CouponIssueRequestEntityMapper::toDomain);
    }

    @Override
    public Optional<CouponIssueRequestModel> findByUserIdAndCouponId(Long userId, Long couponId) {
        return couponIssueRequestJpaRepository.findByUserIdAndCouponId(userId, couponId)
                .map(CouponIssueRequestEntityMapper::toDomain);
    }
}
