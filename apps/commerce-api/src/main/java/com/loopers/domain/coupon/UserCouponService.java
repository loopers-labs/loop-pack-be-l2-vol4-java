package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserCouponService {

    private final UserCouponRepository userCouponRepository;
    private final CouponService couponService;

    // 쿠폰 발급
    @Transactional
    public UserCouponModel issue(Long userId, Long couponId) {
        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
        }
        couponService.decreaseQuantity(couponId);
        return userCouponRepository.save(UserCouponModel.of(userId, couponId));
    }

    @Transactional(readOnly = true)
    public List<UserCouponModel> getMyCoupons(Long userId) {
        return userCouponRepository.findAllByUserId(userId);
    }

    // 쿠폰 발급내역 조회
    @Transactional(readOnly = true)
    public Page<UserCouponModel> getIssues(Long couponId, Pageable pageable) {
        return userCouponRepository.findAllByCouponId(couponId, pageable);
    }

    @Transactional
    public long use(Long userId, Long couponId, long orderAmount) {
        UserCouponModel userCoupon = userCouponRepository.findByUserIdAndCouponIdForUpdate(userId, couponId)
                .orElseThrow(
                    () -> new CoreException(ErrorType.NOT_FOUND, "[userId = " + userId + ", couponId = " + couponId + "] 보유하지 않은 쿠폰입니다.")
                );

        CouponModel coupon = couponService.getCoupon(couponId);
        ZonedDateTime now = ZonedDateTime.now();
        if (coupon.isExpired(now.toLocalDateTime())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }

        long discount = coupon.calculateDiscount(orderAmount);
        userCoupon.use(now);
        return discount;
    }

    @Transactional
    public void restore(Long userId, Long couponId) {
        userCouponRepository.findByUserIdAndCouponIdForUpdate(userId, couponId)
                .ifPresent(UserCouponModel::cancel);
    }
}