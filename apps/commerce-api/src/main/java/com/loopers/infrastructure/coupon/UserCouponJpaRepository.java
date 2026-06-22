package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserCouponJpaRepository extends JpaRepository<UserCouponModel, Long> {
    List<UserCouponModel> findAllByMemberId(Long memberId);
    Page<UserCouponModel> findAllByTemplateId(Long templateId, Pageable pageable);
}
