package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

/**
 * 쿠폰 템플릿 관리(어드민). 대고객 CouponService와 호출자·권한 경계가 갈리고 유스케이스가 묶여 분리한다.
 */
@RequiredArgsConstructor
@Component
public class CouponAdminService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional
    public CouponTemplate create(String name, CouponType type, long discountValue, Long minOrderAmount, ZonedDateTime expiredAt) {
        return couponTemplateRepository.save(new CouponTemplate(name, type, discountValue, minOrderAmount, expiredAt));
    }

    @Transactional(readOnly = true)
    public CouponTemplate getTemplate(Long id) {
        return couponTemplateRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 쿠폰 템플릿을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Page<CouponTemplate> getTemplates(Pageable pageable) {
        return couponTemplateRepository.findAll(pageable);
    }

    @Transactional
    public CouponTemplate update(Long id, String name, CouponType type, long discountValue, Long minOrderAmount, ZonedDateTime expiredAt) {
        CouponTemplate template = getTemplate(id);
        template.update(name, type, discountValue, minOrderAmount, expiredAt);
        return template;
    }

    @Transactional
    public void delete(Long id) {
        CouponTemplate template = getTemplate(id);
        couponTemplateRepository.deleteById(template.getId());
    }

    @Transactional(readOnly = true)
    public Page<IssuedCoupon> getIssues(Long couponTemplateId, Pageable pageable) {
        return issuedCouponRepository.findByCouponTemplateId(couponTemplateId, pageable);
    }
}
