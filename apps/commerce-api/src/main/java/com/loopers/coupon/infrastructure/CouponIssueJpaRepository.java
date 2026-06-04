package com.loopers.coupon.infrastructure;

import com.loopers.coupon.domain.CouponIssueModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface CouponIssueJpaRepository extends JpaRepository<CouponIssueModel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CouponIssueModel> findById(Long id);

    Optional<CouponIssueModel> findByUserIdAndCouponId(Long userId, Long couponId);

    List<CouponIssueModel> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Page<CouponIssueModel> findAllByCouponId(Long couponId, Pageable pageable);
}
