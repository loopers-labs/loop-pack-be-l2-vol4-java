package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssue;
import com.loopers.application.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.UserCouponInfo;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.interfaces.api.coupon.CouponV1Dto.UserCouponResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CouponFacade {

    private final CouponRepository couponRepository;

    @Transactional
    public CouponIssue issueCoupon(Long userId, Long couponTemplateId) {
        CouponTemplate template = couponRepository.findTemplateById(couponTemplateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "조회할 수 없는 쿠폰템플릿입니다."));

        couponRepository.findIssueByUserIdAndTemplateId(userId, couponTemplateId)
                .ifPresent(issue -> {
                    throw new CoreException(ErrorType.CONFLICT, "이미 발급된 쿠폰입니다.");
                });

        CouponIssue newIssue = new CouponIssue(userId, template);
        return couponRepository.saveIssue(newIssue);
    }

    @Transactional(readOnly = true)
    public List<UserCouponResponse> getUsersCoupons(Long userId) {
        List<CouponIssue> issues = couponRepository.findAllIssuesByUserId(userId);
        if (issues.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();

        return issues.stream()
                .map(issue -> UserCouponInfo.of(issue, now))
                .map(info -> new UserCouponResponse(
                        info.id(),
                        info.couponTemplateId(),
                        info.name(),
                        info.type(),
                        info.value(),
                        info.minOrderAmount(),
                        info.maxDiscountAmount(),
                        info.status(),
                        info.expiredAt()
                ))
                .toList();
    }
}
