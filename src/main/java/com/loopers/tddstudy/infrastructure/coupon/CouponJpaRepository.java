package com.loopers.tddstudy.infrastructure.coupon;

import com.loopers.tddstudy.domain.coupon.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface  CouponJpaRepository extends JpaRepository<Coupon,Long> {
}
