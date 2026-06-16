package com.loopers.domain.coupon;

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
        // 1. 템플릿 존재 여부 검증
        CouponTemplate template = couponRepository.findTemplateById(couponTemplateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰 템플릿입니다."));

        // 2. 이미 발급된 쿠폰인지 검증 (중복 발급 방지)
        couponRepository.findIssueByUserIdAndTemplateId(userId, couponTemplateId)
                .ifPresent(issue -> {
                    throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
                });

        // 3. 쿠폰 발급 내역 저장
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
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰 발급 이력입니다."));

        issue.use(orderAmount, java.time.LocalDateTime.now());
        couponRepository.saveIssue(issue);
    }

    public java.math.BigDecimal calculateDiscount(Long couponIssueId, java.math.BigDecimal orderAmount) {
        CouponIssue issue = couponRepository.findIssueById(couponIssueId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰 발급 이력입니다."));

        if (issue.getStatus() == CouponStatus.USED) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 완료된 쿠폰입니다.");
        }

        if (issue.isExpired(java.time.LocalDateTime.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        if (orderAmount.compareTo(issue.getMinOrderAmount()) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액을 충족하지 못했습니다.");
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
