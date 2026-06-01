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
        CouponTemplate couponTemplate = getCouponTemplate(couponTemplateId);
        return userCouponRepository.findByUserIdAndCouponTemplateId(userId, couponTemplate.getId())
            .map(userCoupon -> new CouponIssueResult(couponTemplate, userCoupon, false))
            .orElseGet(() -> issueNewCoupon(userId, couponTemplate));
    }

    @Transactional(readOnly = true)
    public CouponTemplate getCouponTemplate(Long couponTemplateId) {
        return couponTemplateRepository.findActiveById(couponTemplateId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
    }

    private CouponIssueResult issueNewCoupon(Long userId, CouponTemplate couponTemplate) {
        UserCoupon userCoupon = UserCoupon.issue(userId, couponTemplate.getId());
        UserCoupon saved = userCouponRepository.save(userCoupon);
        return new CouponIssueResult(couponTemplate, saved, true);
    }
}
