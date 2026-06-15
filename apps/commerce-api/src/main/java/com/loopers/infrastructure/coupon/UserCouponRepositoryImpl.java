package com.loopers.infrastructure.coupon;

import com.loopers.domain.common.PageResult;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository userCouponJpaRepository;

    @Override
    public Optional<UserCoupon> find(Long id) {
        return userCouponJpaRepository.findById(id);
    }

    @Override
    public Optional<UserCoupon> findByUserIdAndTemplateId(Long userId, Long templateId) {
        return userCouponJpaRepository.findByUserIdAndTemplateId(userId, templateId);
    }

    @Override
    public PageResult<UserCoupon> findAllByUserId(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("id")));
        Page<UserCoupon> result = userCouponJpaRepository.findAllByUserId(userId, pageable);
        return new PageResult<>(
                result.getContent(),
                page,
                size,
                result.hasNext(),
                result.getTotalElements()
        );
    }

    @Override
    public PageResult<UserCoupon> findAllByTemplateId(Long templateId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("id")));
        Page<UserCoupon> result = userCouponJpaRepository.findAllByTemplateId(templateId, pageable);
        return new PageResult<>(
                result.getContent(),
                page,
                size,
                result.hasNext(),
                result.getTotalElements()
        );
    }

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        return userCouponJpaRepository.save(userCoupon);
    }
}
