package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class CouponTemplateService {

    private final CouponTemplateRepository couponTemplateRepository;

    @Transactional
    public CouponTemplateModel create(CouponTemplateModel template) {
        return couponTemplateRepository.save(template);
    }

    @Transactional(readOnly = true)
    public CouponTemplateModel getById(Long id) {
        return couponTemplateRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[templateId = " + id + "] 쿠폰 템플릿을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Page<CouponTemplateModel> getAll(PageRequest pageRequest) {
        return couponTemplateRepository.findAll(pageRequest);
    }

    @Transactional
    public CouponTemplateModel update(Long id, String name, boolean isActive) {
        CouponTemplateModel template = couponTemplateRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[templateId = " + id + "] 쿠폰 템플릿을 찾을 수 없습니다."));
        template.update(name, isActive);
        return template;
    }

    @Transactional
    public void delete(Long id) {
        CouponTemplateModel template = couponTemplateRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[templateId = " + id + "] 쿠폰 템플릿을 찾을 수 없습니다."));
        template.block();
    }
}
