package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public CouponIssueResult issueCoupon(Long userId, Long couponTemplateId) {
        CouponTemplate couponTemplate = getCouponTemplateForIssue(couponTemplateId);
        return userCouponRepository.findIssuedCoupon(userId, couponTemplate.getId())
            .map(issuedCoupon -> CouponIssueResult.alreadyIssued(couponTemplate, issuedCoupon))
            .orElseGet(() -> {
                UserCoupon issuedCoupon = couponTemplate.issue(userId);
                UserCoupon savedCoupon = userCouponRepository.save(issuedCoupon);
                return CouponIssueResult.issued(couponTemplate, savedCoupon);
            });
    }

    @Transactional(readOnly = true)
    public CouponIssueResult getAlreadyIssuedCoupon(Long userId, Long couponTemplateId) {
        CouponTemplate couponTemplate = getCouponTemplateForIssue(couponTemplateId);
        UserCoupon issuedCoupon = userCouponRepository.findIssuedCoupon(userId, couponTemplate.getId())
            .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR, "발급된 쿠폰을 확인할 수 없습니다."));
        return CouponIssueResult.alreadyIssued(couponTemplate, issuedCoupon);
    }

    public CouponTemplate getCouponTemplateForIssue(Long couponTemplateId) {
        return couponTemplateRepository.findIssuingCoupon(couponTemplateId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
    }

}
