package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCouponModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCouponModel, Long> {

    Page<IssuedCouponModel> findAllByCouponTemplateId(Long couponTemplateId, Pageable pageable);

    List<IssuedCouponModel> findAllByUserId(Long userId);

    boolean existsByCouponTemplateIdAndUserId(Long couponTemplateId, Long userId);
}
