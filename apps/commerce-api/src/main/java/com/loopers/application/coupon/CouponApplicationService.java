package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponEntity;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplateEntity;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Service
public class CouponApplicationService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final CouponRepository couponRepository;

    @Transactional
    public CouponTemplateInfo createTemplate(String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        CouponTemplateEntity template = new CouponTemplateEntity(name, type, value, minOrderAmount, expiredAt);
        return CouponTemplateInfo.from(couponTemplateRepository.save(template));
    }

    @Transactional(readOnly = true)
    public CouponTemplateInfo getTemplate(String couponTemplateId) {
        return CouponTemplateInfo.from(findTemplateOrThrow(couponTemplateId));
    }

    @Transactional(readOnly = true)
    public Page<CouponTemplateInfo> getTemplates(Pageable pageable) {
        return couponTemplateRepository.findAll(pageable).map(CouponTemplateInfo::from);
    }

    @Transactional
    public void updateTemplate(String couponTemplateId, String name, Long minOrderAmount, ZonedDateTime expiredAt) {
        CouponTemplateEntity template = findTemplateOrThrow(couponTemplateId);
        template.update(name, minOrderAmount, expiredAt);
        couponTemplateRepository.save(template);
    }

    @Transactional
    public void deleteTemplate(String couponTemplateId) {
        CouponTemplateEntity template = findTemplateOrThrow(couponTemplateId);
        template.delete();
        couponTemplateRepository.save(template);
        couponRepository.softDeleteAllByTemplateId(couponTemplateId);
    }

    @Transactional(readOnly = true)
    public Page<CouponInfo> getTemplateIssues(String couponTemplateId, Pageable pageable) {
        CouponTemplateEntity template = findTemplateOrThrow(couponTemplateId);
        return couponRepository.findAllByCouponTemplateId(couponTemplateId, pageable)
                .map(coupon -> CouponInfo.from(coupon, template));
    }

    @Transactional
    public CouponInfo issueCoupon(String userId, String couponTemplateId) {
        CouponTemplateEntity template = findTemplateOrThrow(couponTemplateId);
        if (template.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰 템플릿입니다.");
        }
        CouponEntity coupon = couponRepository.save(new CouponEntity(couponTemplateId, userId));
        return CouponInfo.from(coupon, template);
    }

    @Transactional(readOnly = true)
    public Page<CouponInfo> getMyCoupons(String userId, Pageable pageable) {
        return couponRepository.findAllByUserId(userId, pageable)
                .map(coupon -> CouponInfo.from(coupon, findTemplateOrThrow(coupon.getCouponTemplateId())));
    }

    @Transactional
    public Long useCoupon(String couponId, String userId, Long originalAmount) {
        CouponEntity coupon = couponRepository.findByIdWithLock(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        if (!coupon.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 쿠폰만 사용할 수 있습니다.");
        }
        CouponTemplateEntity template = findTemplateOrThrow(coupon.getCouponTemplateId());
        if (coupon.resolveStatus(template.getExpiredAt()) == CouponStatus.EXPIRED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        template.validateMinimumOrderAmount(originalAmount);
        coupon.use();
        Long discountAmount = template.calculateDiscount(originalAmount);
        couponRepository.save(coupon);
        return discountAmount;
    }

    private CouponTemplateEntity findTemplateOrThrow(String couponTemplateId) {
        return couponTemplateRepository.findById(couponTemplateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다."));
    }
}
