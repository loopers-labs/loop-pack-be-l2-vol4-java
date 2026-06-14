package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponJpaRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByIdAndDeletedAtIsNull(Long id);
    Page<Coupon> findByDeletedAtIsNull(Pageable pageable);
    long countByDeletedAtIsNull();
}
