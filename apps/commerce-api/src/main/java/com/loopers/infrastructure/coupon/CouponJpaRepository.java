package com.loopers.infrastructure.coupon;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CouponJpaRepository extends JpaRepository<CouponEntity, Long> {
    List<CouponEntity> findAllByOrderByIdDesc(Pageable pageable);
}
