package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
}
