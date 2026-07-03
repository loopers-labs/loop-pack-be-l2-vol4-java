package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponIssueRequestRepositoryImpl implements CouponIssueRequestRepository {

    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @Override
    public CouponIssueRequestModel save(CouponIssueRequestModel request) {
        try {
            return couponIssueRequestJpaRepository.save(request);
        } catch (DataIntegrityViolationException e) {
            // (coupon_template_id, user_id) UNIQUE 제약 위반 → 동일 사용자의 중복 발급 요청 방어
            throw new CoreException(ErrorType.CONFLICT, "이미 발급 요청된 쿠폰입니다.");
        }
    }

    @Override
    public Optional<CouponIssueRequestModel> findByRequestId(String requestId) {
        return couponIssueRequestJpaRepository.findByRequestId(requestId);
    }
}
