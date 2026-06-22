package com.loopers.application.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponTemplateService couponTemplateService;
    private final UserCouponService userCouponService;

    public UserCouponModel issue(Long memberId, Long templateId) {
        var template = couponTemplateService.getById(templateId);
        if (!template.canIssue()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급할 수 없는 쿠폰입니다.");
        }
        return userCouponService.save(new UserCouponModel(memberId, templateId));
    }
}
