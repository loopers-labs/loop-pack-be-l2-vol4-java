package com.loopers.domain.coupon;

import com.loopers.domain.common.PageResult;

import java.util.Optional;

public interface UserCouponRepository {

    Optional<UserCoupon> find(Long id);
    Optional<UserCoupon> findByUserIdAndTemplateId(Long userId, Long templateId);
    PageResult<UserCoupon> findAllByUserId(Long userId, int page, int size);
    PageResult<UserCoupon> findAllByTemplateId(Long templateId, int page, int size);
    UserCoupon save(UserCoupon userCoupon);

}
