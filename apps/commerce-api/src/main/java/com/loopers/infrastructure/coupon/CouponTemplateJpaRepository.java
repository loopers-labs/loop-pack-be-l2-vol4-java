package com.loopers.infrastructure.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplateJpaEntity, String> {
    Optional<CouponTemplateJpaEntity> findByIdAndDeletedAtIsNull(String id);
    Page<CouponTemplateJpaEntity> findAllByDeletedAtIsNull(Pageable pageable);
}
