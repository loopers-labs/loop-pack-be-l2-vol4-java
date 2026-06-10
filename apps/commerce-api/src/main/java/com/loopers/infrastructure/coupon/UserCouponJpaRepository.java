package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCouponJpaRepository extends JpaRepository<UserCouponModel, Long> {
}
