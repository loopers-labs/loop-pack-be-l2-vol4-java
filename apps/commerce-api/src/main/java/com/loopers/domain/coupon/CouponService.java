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
        if (userCouponRepository.findIssuedCoupon(userId, couponTemplate.getId()).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급된 쿠폰입니다.");
        }

        UserCoupon issuedCoupon = couponTemplate.issue(userId);
        UserCoupon savedCoupon = userCouponRepository.save(issuedCoupon);
        return CouponIssueResult.issued(couponTemplate, savedCoupon);
    }

    public CouponTemplate getCouponTemplate(Long couponTemplateId) {
        return couponTemplateRepository.findIssuingCoupon(couponTemplateId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
    }

}
