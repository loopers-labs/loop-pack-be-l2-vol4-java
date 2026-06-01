package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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
    public Optional<CouponIssueResult> findAlreadyIssuedCoupon(Long userId, Long couponTemplateId) {
        CouponTemplate couponTemplate = getCouponTemplateForIssue(couponTemplateId);
        return userCouponRepository.findIssuedCoupon(userId, couponTemplate.getId())
            .map(issuedCoupon -> CouponIssueResult.alreadyIssued(couponTemplate, issuedCoupon));
    }

    public CouponTemplate getCouponTemplateForIssue(Long couponTemplateId) {
        return couponTemplateRepository.findIssuingCoupon(couponTemplateId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));
    }

}
