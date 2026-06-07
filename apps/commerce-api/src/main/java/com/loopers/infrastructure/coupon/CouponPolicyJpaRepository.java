package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CouponPolicyJpaRepository extends JpaRepository<CouponPolicy, Long> {
    Optional<CouponPolicy> findByIdAndDeletedAtIsNull(Long id);
    List<CouponPolicy> findAllByIdIn(Collection<Long> ids);
}
