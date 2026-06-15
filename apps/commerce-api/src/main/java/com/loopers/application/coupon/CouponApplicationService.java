package com.loopers.application.coupon;

import com.loopers.domain.common.PageResult;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.DiscountPolicy;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class CouponApplicationService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public CouponInfo.Issued issue(Long userId, Long templateId) {
        CouponTemplate template = couponTemplateRepository.find(templateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));

        userCouponRepository.findByUserIdAndTemplateId(userId, templateId)
                .ifPresent(existing -> {
                    throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
                });

        UserCoupon issued = UserCoupon.issue(userId, template, ZonedDateTime.now());
        UserCoupon saved = userCouponRepository.save(issued);
        return CouponInfo.Issued.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResult<CouponInfo.MyCoupon> getMyCoupons(Long userId, int page, int size) {
        ZonedDateTime now = ZonedDateTime.now();
        return userCouponRepository.findAllByUserId(userId, page, size)
                .map(coupon -> CouponInfo.MyCoupon.from(coupon, now));
    }

    @Transactional
    public CouponInfo.Template registerTemplate(CouponCriteria.RegisterTemplate command) {
        DiscountPolicy policy = DiscountPolicy.of(command.discountType(), command.discountValue(), command.minOrderAmount());
        CouponTemplate template = CouponTemplate.create(command.name(), policy, command.validDays());
        return CouponInfo.Template.from(couponTemplateRepository.save(template));
    }

    @Transactional
    public void modifyTemplate(CouponCriteria.ModifyTemplate command) {
        CouponTemplate template = couponTemplateRepository.find(command.id())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        DiscountPolicy policy = DiscountPolicy.of(command.discountType(), command.discountValue(), command.minOrderAmount());
        template.modify(command.name(), policy, command.validDays());
    }

    @Transactional
    public void deleteTemplate(Long id) {
        CouponTemplate template = couponTemplateRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        template.delete();
    }

    @Transactional(readOnly = true)
    public CouponInfo.Template getTemplate(Long id) {
        CouponTemplate template = couponTemplateRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        return CouponInfo.Template.from(template);
    }

    @Transactional(readOnly = true)
    public PageResult<CouponInfo.Template> getTemplatePage(int page, int size) {
        return couponTemplateRepository.findAll(page, size).map(CouponInfo.Template::from);
    }

    @Transactional(readOnly = true)
    public PageResult<CouponInfo.IssueHistoryItem> getIssueHistory(Long templateId, int page, int size) {
        ZonedDateTime now = ZonedDateTime.now();
        return userCouponRepository.findAllByTemplateId(templateId, page, size)
                .map(coupon -> CouponInfo.IssueHistoryItem.from(coupon, now));
    }
}
