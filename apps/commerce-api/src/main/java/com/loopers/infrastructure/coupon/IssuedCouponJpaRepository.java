package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.model.IssuedCoupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCoupon, Long> {

    boolean existsByMemberIdAndCouponTemplateId(Long memberId, Long couponTemplateId);

    List<IssuedCoupon> findAllByMemberId(Long memberId);

    Page<IssuedCoupon> findAllByCouponTemplateId(Long couponTemplateId, Pageable pageable);
}
