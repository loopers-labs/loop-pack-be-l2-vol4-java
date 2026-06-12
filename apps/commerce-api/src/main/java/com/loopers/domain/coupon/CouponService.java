package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public CouponTemplate createTemplate(String name, CouponType type, Long value, Long minOrderAmount,
                                         java.time.ZonedDateTime expiredAt) {
        return couponTemplateRepository.save(new CouponTemplate(name, type, value, minOrderAmount, expiredAt));
    }

    @Transactional(readOnly = true)
    public CouponTemplate getTemplate(Long templateId) {
        return couponTemplateRepository.find(templateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + templateId + "] 쿠폰을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<CouponTemplate> getAllTemplates() {
        return couponTemplateRepository.findAll();
    }

    @Transactional
    public CouponTemplate updateTemplate(Long templateId, String name, CouponType type, Long value,
                                         Long minOrderAmount, java.time.ZonedDateTime expiredAt) {
        CouponTemplate template = couponTemplateRepository.find(templateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + templateId + "] 쿠폰을 찾을 수 없습니다."));
        template.update(name, type, value, minOrderAmount, expiredAt);
        return template;
    }

    @Transactional
    public void deleteTemplate(Long templateId) {
        couponTemplateRepository.find(templateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + templateId + "] 쿠폰을 찾을 수 없습니다."))
                .delete();
    }

    @Transactional
    public UserCoupon issueCoupon(Long memberId, Long templateId) {
        CouponTemplate template = couponTemplateRepository.find(templateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + templateId + "] 쿠폰을 찾을 수 없습니다."));
        if (template.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급할 수 없습니다.");
        }
        return userCouponRepository.save(new UserCoupon(memberId, template));
    }

    @Transactional(readOnly = true)
    public List<UserCoupon> getMyCoupons(Long memberId) {
        return userCouponRepository.findAllByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public Page<UserCoupon> getIssuedCoupons(Long couponTemplateId, Pageable pageable) {
        couponTemplateRepository.find(couponTemplateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + couponTemplateId + "] 쿠폰을 찾을 수 없습니다."));
        return userCouponRepository.findAllByCouponTemplateId(couponTemplateId, pageable);
    }

    /**
     * 비관적 락으로 쿠폰을 조회하여 유효성 검증 및 사용 처리를 원자적으로 수행한다.
     * 할인 금액을 계산하여 반환한다.
     */
    @Transactional
    public long validateAndUseCoupon(Long memberId, Long userCouponId, long orderAmount) {
        UserCoupon userCoupon = userCouponRepository.findWithLock(userCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));

        if (!userCoupon.isOwnedBy(memberId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "본인 소유의 쿠폰만 사용할 수 있습니다.");
        }

        CouponTemplate template = couponTemplateRepository.find(userCoupon.getCouponTemplateId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다."));

        long discountAmount = template.calculateDiscount(orderAmount);
        userCoupon.use();

        return discountAmount;
    }
}
