package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

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

    public List<UserCouponInfo> getMyCoupons(Long memberId) {
        return userCouponService.getMyCoupons(memberId);
    }

    // ─── Admin ────────────────────────────────────────────────

    public CouponTemplateModel createTemplate(String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt) {
        return couponTemplateService.create(new CouponTemplateModel(name, type, value, minOrderAmount, expiredAt));
    }

    public Page<CouponTemplateModel> getTemplates(PageRequest pageRequest) {
        return couponTemplateService.getAll(pageRequest);
    }

    public CouponTemplateModel getTemplate(Long templateId) {
        return couponTemplateService.getById(templateId);
    }

    public CouponTemplateModel updateTemplate(Long templateId, String name, boolean isActive) {
        return couponTemplateService.update(templateId, name, isActive);
    }

    public void deleteTemplate(Long templateId) {
        couponTemplateService.delete(templateId);
    }

    public Page<UserCouponModel> getIssuances(Long templateId, PageRequest pageRequest) {
        return userCouponService.getIssuances(templateId, pageRequest);
    }
}
