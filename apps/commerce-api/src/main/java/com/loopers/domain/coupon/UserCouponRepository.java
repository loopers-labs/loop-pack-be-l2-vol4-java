package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {
    UserCouponModel save(UserCouponModel userCoupon);
    Optional<UserCouponModel> findById(Long id);
    List<UserCouponModel> findAllByMemberId(Long memberId);
    Page<UserCouponModel> findAllByTemplateId(Long templateId, PageRequest pageRequest);
}
