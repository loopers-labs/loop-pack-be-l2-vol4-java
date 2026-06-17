package com.loopers.domain.coupon.repository;

import com.loopers.domain.coupon.model.IssuedCoupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface IssuedCouponRepository {
    IssuedCoupon save(IssuedCoupon issuedCoupon);
    Optional<IssuedCoupon> findById(Long id);
    boolean existsByMemberIdAndCouponTemplateId(Long memberId, Long couponTemplateId);
    List<IssuedCoupon> findAllByMemberId(Long memberId);
    Page<IssuedCoupon> findAllByCouponTemplateId(Long couponTemplateId, Pageable pageable);
}
