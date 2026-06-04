package com.loopers.coupon.infrastructure;

import com.loopers.coupon.domain.CouponIssueModel;
import com.loopers.coupon.domain.CouponIssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponIssueRepositoryImpl implements CouponIssueRepository {

    private final CouponIssueJpaRepository couponIssueJpaRepository;

    @Override
    public CouponIssueModel save(CouponIssueModel issue) {
        return couponIssueJpaRepository.save(issue);
    }

    @Override
    public Optional<CouponIssueModel> findById(Long id) {
        return couponIssueJpaRepository.findById(id);
    }

    @Override
    public Optional<CouponIssueModel> findByUserIdAndCouponId(Long userId, Long couponId) {
        return couponIssueJpaRepository.findByUserIdAndCouponId(userId, couponId);
    }

    @Override
    public List<CouponIssueModel> findAllByUserId(Long userId) {
        return couponIssueJpaRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public List<CouponIssueModel> findAllByCouponId(Long couponId, int page, int size) {
        return couponIssueJpaRepository.findAllByCouponId(couponId, PageRequest.of(page, size)).getContent();
    }
}
