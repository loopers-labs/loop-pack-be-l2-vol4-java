package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class CouponTemplateService {

    private final CouponTemplateRepository couponTemplateRepository;

    public CouponTemplateModel create(String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        if (couponTemplateRepository.existsByName(name)) {
            throw new CoreException(ErrorType.CONFLICT, "[name = " + name + "] 이미 존재하는 쿠폰명입니다.");
        }
        return couponTemplateRepository.save(new CouponTemplateModel(name, type, value, minOrderAmount, expiredAt));
    }

    /** 어드민용 — 삭제된 템플릿도 조회 */
    public CouponTemplateModel get(UUID id) {
        return couponTemplateRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 쿠폰 템플릿을 찾을 수 없습니다."));
    }

    /** 발급용 — 활성 템플릿만 조회 */
    public CouponTemplateModel getActive(UUID id) {
        return couponTemplateRepository.findActive(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 쿠폰 템플릿을 찾을 수 없습니다."));
    }

    public Page<CouponTemplateModel> getList(Pageable pageable) {
        return couponTemplateRepository.findAll(pageable);
    }

    public CouponTemplateModel update(UUID id, String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        CouponTemplateModel template = get(id);
        template.update(name, type, value, minOrderAmount, expiredAt);
        return template;
    }

    public void delete(UUID id) {
        CouponTemplateModel template = get(id);
        template.delete();
    }
}
