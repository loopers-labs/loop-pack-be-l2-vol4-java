package com.loopers.domain.coupon;

import com.loopers.application.coupon.CouponRepository;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

    public CouponIssue issue(Long userId, Long couponTemplateId) {
        // 1. ??쀫탣??鈺곕똻????? 野꺜筌?
        CouponTemplate template = couponRepository.findTemplateById(couponTemplateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "鈺곕똻???? ??낅뮉 ?묒쥚猷???쀫탣?깆슦???덈뼄."));

        // 2. ??? 獄쏆뮄????묒쥚猷?紐? 野꺜筌?(餓λ쵎??獄쏆뮄??獄쎻뫗?)
        couponRepository.findIssueByUserIdAndTemplateId(userId, couponTemplateId)
                .ifPresent(issue -> {
                    throw new CoreException(ErrorType.CONFLICT, "??? 獄쏆뮄?믦쳸?? ?묒쥚猷??낅빍??");
                });

        // 3. ?묒쥚猷?獄쏆뮄????곷열 ????
        CouponIssue newIssue = new CouponIssue(userId, template);
        return couponRepository.saveIssue(newIssue);
    }

    public List<UserCouponInfo> getUsersCoupons(Long userId) {
        List<CouponIssue> issues = couponRepository.findAllIssuesByUserId(userId);
        if (issues.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();

        return issues.stream()
                .map(issue -> UserCouponInfo.of(issue, now))
                .toList();
    }

    public void completeCouponUse(Long couponIssueId, java.math.BigDecimal orderAmount) {
        CouponIssue issue = couponRepository.findIssueById(couponIssueId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "鈺곕똻???? ??낅뮉 ?묒쥚猷?獄쏆뮄???????낅빍??"));

        issue.use(orderAmount, java.time.LocalDateTime.now());
        couponRepository.saveIssue(issue);
    }

    public java.math.BigDecimal calculateDiscount(Long couponIssueId, java.math.BigDecimal orderAmount) {
        CouponIssue issue = couponRepository.findIssueById(couponIssueId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "鈺곕똻???? ??낅뮉 ?묒쥚猷?獄쏆뮄???????낅빍??"));

        if (issue.getStatus() == CouponStatus.USED) {
            throw new CoreException(ErrorType.CONFLICT, "??? ?????袁⑥┷???묒쥚猷??낅빍??");
        }

        if (issue.isExpired(java.time.LocalDateTime.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "筌띾슢利???묒쥚猷??낅빍??");
        }
        if (orderAmount.compareTo(issue.getMinOrderAmount()) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "筌ㅼ뮇??雅뚯눖揆 疫뀀뜆釉???겸뫗???? 筌륁궢六??щ빍??");
        }

        java.math.BigDecimal discount = java.math.BigDecimal.ZERO;
        if (issue.getCouponType() == CouponType.FIXED) {
            discount = issue.getDiscountValue();
            if (discount.compareTo(orderAmount) > 0) {
                discount = orderAmount;
            }
        } else if (issue.getCouponType() == CouponType.RATE) {
            java.math.BigDecimal rate = issue.getDiscountValue().divide(new java.math.BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP);
            discount = orderAmount.multiply(rate).setScale(0, java.math.RoundingMode.HALF_UP);

            if (issue.getMaxDiscountAmount() != null && discount.compareTo(issue.getMaxDiscountAmount()) > 0) {
                discount = issue.getMaxDiscountAmount();
            }
        }
        return discount;
    }
}
