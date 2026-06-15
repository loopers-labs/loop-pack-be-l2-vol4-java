package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplate, Long> {

    Optional<CouponTemplate> findByIdAndDeletedAtIsNull(Long id);

    Page<CouponTemplate> findAllByDeletedAtIsNull(Pageable pageable);
}
