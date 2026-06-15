package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class UserCouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    /**
     * FR-C-01. 쿠폰 발급 — 1인 1회 제한.
     */
    @Transactional
    public UserCouponInfo issue(Long userId, Long couponId) {
        CouponModel coupon = couponRepository.findById(couponId)
            .filter(c -> !c.isDeleted())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다."));

        if (coupon.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급할 수 없습니다.");
        }

        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
        }

        try {
            return UserCouponInfo.from(userCouponRepository.save(new UserCouponModel(userId, coupon)));
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
        }
    }

    /**
     * FR-C-02. 내 쿠폰 목록 조회 — AVAILABLE / USED / EXPIRED 상태 포함.
     */
    @Transactional(readOnly = true)
    public Page<UserCouponInfo> getMyCoupons(Long userId, Pageable pageable) {
        return userCouponRepository.findAllByUserId(userId, pageable).map(UserCouponInfo::from);
    }
}
