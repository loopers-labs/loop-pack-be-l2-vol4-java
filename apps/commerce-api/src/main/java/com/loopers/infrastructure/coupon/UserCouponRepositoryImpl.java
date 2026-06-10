package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class UserCouponRepositoryImpl implements UserCouponRepository {

    private final UserCouponJpaRepository userCouponJpaRepository;

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        try {
            return userCouponJpaRepository.saveAndFlush(userCoupon);
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급된 쿠폰입니다.");
        }
    }

    @Override
    public Optional<UserCoupon> findById(Long userCouponId) {
        return userCouponJpaRepository.findById(userCouponId);
    }

    @Override
    public Optional<UserCoupon> findIssuedCoupon(Long userId, Long couponTemplateId) {
        return userCouponJpaRepository.findByOwnerUserIdAndCouponTemplateIdValue(userId, couponTemplateId);
    }

    @Override
    public PageResult<UserCoupon> findAllByCouponTemplateId(Long couponTemplateId, PageQuery query) {
        Pageable pageable = PageRequest.of(query.page(), query.size(), Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
        ));
        return PageResult.from(userCouponJpaRepository.findByCouponTemplateIdValue(couponTemplateId, pageable));
    }

    @Override
    public void applyUse(UserCoupon userCoupon) {
        try {
            userCouponJpaRepository.saveAndFlush(userCoupon);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new CoreException(ErrorType.CONFLICT, "사용할 수 없는 쿠폰입니다.");
        }
    }
}
