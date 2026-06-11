package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional(readOnly = true)
    public CouponModel getCoupon(Long couponId) {
        return couponRepository.find(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
    }

    @Transactional(readOnly = true)
    public List<UserCouponModel> getUserCoupons(Long userId) {
        return userCouponRepository.findAllByUserId(userId);
    }

    @Transactional
    public UserCouponModel useCoupon(Long userId, Long userCouponId) {
        UserCouponModel userCoupon = userCouponRepository.findForUpdate(userCouponId)
            .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다."));
        if (!userCoupon.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다.");
        }
        userCoupon.use();
        return userCoupon;
    }

    @Transactional
    public UserCouponModel issueCoupon(Long userId, Long couponId) {
        couponRepository.find(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));

        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
        }

        return userCouponRepository.save(new UserCouponModel(userId, couponId));
    }
}
