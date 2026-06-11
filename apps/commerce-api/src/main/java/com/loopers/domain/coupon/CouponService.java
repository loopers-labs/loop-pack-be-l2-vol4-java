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
        couponRepository.findTemplateById(couponTemplateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰 템플릿입니다."));

        // 2. 이미 발급된 쿠폰인지 검증 (중복 발급 방지)
        couponRepository.findIssueByUserIdAndTemplateId(userId, couponTemplateId)
                .ifPresent(issue -> {
                    throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
                });

        // 3. 쿠폰 발급 내역 저장
        CouponIssue newIssue = new CouponIssue(userId, couponTemplateId);
        return couponRepository.saveIssue(newIssue);
    }

    public List<UserCouponInfo> getUsersCoupons(Long userId) {
        List<CouponIssue> issues = couponRepository.findAllIssuesByUserId(userId);
        if (issues.isEmpty()) {
            return List.of();
        }

        List<Long> templateIds = issues.stream()
                .map(CouponIssue::getCouponTemplateId)
                .distinct()
                .toList();

        List<CouponTemplate> templates = couponRepository.findTemplatesByIds(templateIds);
        java.util.Map<Long, CouponTemplate> templateMap = templates.stream()
                .collect(java.util.stream.Collectors.toMap(CouponTemplate::getId, t -> t));

        LocalDateTime now = LocalDateTime.now();

        return issues.stream()
                .map(issue -> {
                    CouponTemplate template = templateMap.get(issue.getCouponTemplateId());
                    if (template == null) {
                        throw new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다.");
                    }
                    return UserCouponInfo.of(issue, template, now);
                })
                .toList();
    }

    public void completeCouponUse(Long couponIssueId, java.math.BigDecimal orderAmount) {
        CouponIssue issue = couponRepository.findIssueById(couponIssueId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰 발급 이력입니다."));

        CouponTemplate template = couponRepository.findTemplateById(issue.getCouponTemplateId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰 템플릿입니다."));

        issue.use(template, orderAmount, java.time.LocalDateTime.now());
        couponRepository.saveIssue(issue);
    }

    public java.math.BigDecimal calculateDiscount(Long couponIssueId, java.math.BigDecimal orderAmount) {
        CouponIssue issue = couponRepository.findIssueById(couponIssueId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰 발급 이력입니다."));

        if (issue.getStatus() == CouponStatus.USED) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 완료된 쿠폰입니다.");
        }

        CouponTemplate template = couponRepository.findTemplateById(issue.getCouponTemplateId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰 템플릿입니다."));

        if (template.isExpired(java.time.LocalDateTime.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        if (orderAmount.compareTo(template.getMinOrderAmount()) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액을 충족하지 못했습니다.");
        }

        java.math.BigDecimal discount = java.math.BigDecimal.ZERO;
        if (template.getType() == CouponType.FIXED) {
            discount = template.getValue();
            if (discount.compareTo(orderAmount) > 0) {
                discount = orderAmount;
            }
        } else if (template.getType() == CouponType.RATE) {
            java.math.BigDecimal rate = template.getValue().divide(new java.math.BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP);
            discount = orderAmount.multiply(rate).setScale(0, java.math.RoundingMode.HALF_UP);

            if (template.getMaxDiscountAmount() != null && discount.compareTo(template.getMaxDiscountAmount()) > 0) {
                discount = template.getMaxDiscountAmount();
            }
        }
        return discount;
    }
}
