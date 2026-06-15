package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.DiscountPolicy;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 쿠폰 템플릿 관리(ADMIN) 유즈케이스.
 * 등록/조회/수정/삭제 + 발급 내역 조회를 조율한다.
 */
@RequiredArgsConstructor
@Component
public class CouponAdminFacade {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public CouponTemplateInfo create(CouponCommand.Create cmd) {
        DiscountPolicy policy = toPolicy(cmd.type(), cmd.value(), cmd.minOrderAmount());
        CouponModel saved = couponRepository.save(CouponModel.create(cmd.name(), policy, cmd.expiredAt()));
        return CouponTemplateInfo.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CouponTemplateInfo> getTemplates(int page, int size) {
        return couponRepository.findAll(page, size).stream()
                .map(CouponTemplateInfo::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CouponTemplateInfo getTemplate(Long couponId) {
        return CouponTemplateInfo.from(findTemplate(couponId));
    }

    @Transactional
    public CouponTemplateInfo update(Long couponId, CouponCommand.Update cmd) {
        CouponModel template = findTemplate(couponId);
        template.update(cmd.name(), toPolicy(cmd.type(), cmd.value(), cmd.minOrderAmount()), cmd.expiredAt());
        return CouponTemplateInfo.from(template);   // 변경감지로 커밋 시 UPDATE
    }

    @Transactional
    public void delete(Long couponId) {
        findTemplate(couponId).delete();   // soft delete (BaseEntity), 변경감지로 반영
    }

    @Transactional(readOnly = true)
    public List<CouponInfo> getIssues(Long couponId, int page, int size) {
        return userCouponRepository.findByCouponId(couponId, page, size).stream()
                .map(CouponInfo::from)
                .toList();
    }

    private CouponModel findTemplate(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "[id = " + couponId + "] 쿠폰을 찾을 수 없습니다."));
    }

    private DiscountPolicy toPolicy(DiscountType type, long value, Long minOrderAmount) {
        Money min = Money.of(minOrderAmount == null ? 0L : minOrderAmount);
        return DiscountPolicy.of(type, value, min);
    }
}
