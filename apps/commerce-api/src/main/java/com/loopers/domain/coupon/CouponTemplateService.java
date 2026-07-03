package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponTemplateService {

    private final CouponTemplateRepository couponTemplateRepository;

    @Transactional
    public CouponTemplate create(String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt) {
        return couponTemplateRepository.save(new CouponTemplate(name, type, value, minOrderAmount, expiredAt));
    }

    @Transactional(readOnly = true)
    public CouponTemplate get(Long id) {
        return couponTemplateRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 쿠폰 템플릿을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<CouponTemplate> list(int page, int size) {
        return couponTemplateRepository.findAll(page, size);
    }

    @Transactional
    public CouponTemplate update(Long id, String name, CouponType type, Long value, Long minOrderAmount, LocalDateTime expiredAt) {
        CouponTemplate template = get(id);
        template.update(name, type, value, minOrderAmount, expiredAt);
        return couponTemplateRepository.save(template);
    }

    @Transactional
    public void delete(Long id) {
        get(id); // 존재 검증
        couponTemplateRepository.delete(id);
    }
}
