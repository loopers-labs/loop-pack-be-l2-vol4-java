package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponIssueJpaRepository extends JpaRepository<CouponIssue, Long> {
    Optional<CouponIssue> findByUserIdAndCouponTemplateId(Long userId, Long couponTemplateId);
    List<CouponIssue> findAllByUserId(Long userId);
    Page<CouponIssue> findAllByCouponTemplateId(Long couponTemplateId, Pageable pageable);
}
