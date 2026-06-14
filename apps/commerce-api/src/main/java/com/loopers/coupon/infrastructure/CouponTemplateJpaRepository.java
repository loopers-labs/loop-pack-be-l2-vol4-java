package com.loopers.coupon.infrastructure;

import com.loopers.coupon.domain.CouponTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplate, Long> {

    Optional<CouponTemplate> findByIdAndDeletedAtIsNull(Long couponTemplateId);

    Page<CouponTemplate> findByDeletedAtIsNull(Pageable pageable);
}
