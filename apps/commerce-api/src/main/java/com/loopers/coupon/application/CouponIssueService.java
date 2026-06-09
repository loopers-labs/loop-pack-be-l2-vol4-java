package com.loopers.coupon.application;

import com.loopers.coupon.domain.Coupon;
import com.loopers.coupon.domain.CouponErrorCode;
import com.loopers.coupon.domain.CouponRepository;
import com.loopers.coupon.domain.UserCoupon;
import com.loopers.coupon.domain.UserCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class CouponIssueService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public CouponResult.IssueDetail issue(Long userId, Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, CouponErrorCode.COUPON_NOT_FOUND));
        if (userCouponRepository.existsByCouponIdAndUserId(couponId, userId)) {
            throw new CoreException(ErrorType.CONFLICT, CouponErrorCode.COUPON_ALREADY_ISSUED);
        }
        try {
            UserCoupon issued = userCouponRepository.save(coupon.issueTo(userId));
            return CouponResult.IssueDetail.from(issued, ZonedDateTime.now());
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, CouponErrorCode.COUPON_ALREADY_ISSUED);
        }
    }
}
