package com.loopers.infrastructure.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponJpaRepository extends JpaRepository<CouponEntity, Long> {
    Optional<CouponEntity> findByIdAndDeletedAtIsNull(Long id);
    Page<CouponEntity> findAllByDeletedAtIsNull(Pageable pageable);
}
