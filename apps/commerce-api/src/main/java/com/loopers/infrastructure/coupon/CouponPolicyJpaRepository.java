package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CouponPolicyJpaRepository extends JpaRepository<CouponPolicy, Long> {
    List<CouponPolicy> findAllByIdIn(Collection<Long> ids);
}
