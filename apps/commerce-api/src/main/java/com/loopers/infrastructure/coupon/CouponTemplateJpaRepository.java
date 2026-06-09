package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplateModel, UUID> {
    Optional<CouponTemplateModel> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByName(String name);
}
