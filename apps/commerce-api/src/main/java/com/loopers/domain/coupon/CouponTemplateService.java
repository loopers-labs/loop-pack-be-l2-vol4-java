package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class CouponTemplateService {

    private final CouponTemplateRepository couponTemplateRepository;

    public Page<CouponTemplateModel> findAll(Pageable pageable) {
        return couponTemplateRepository.findAll(pageable);
    }

    public CouponTemplateModel getById(Long couponTemplateId) {
        return couponTemplateRepository.findById(couponTemplateId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿이 존재하지 않습니다."));
    }

    public Map<Long, CouponTemplateModel> getMapByIds(Set<Long> ids) {
        return couponTemplateRepository.findAllByIds(ids).stream()
                .collect(Collectors.toMap(CouponTemplateModel::getId, Function.identity()));
    }

    public CouponTemplateModel createTemplate(String name, CouponType type, BigDecimal value,
                                              BigDecimal minOrderAmount, ZonedDateTime expiredAt, int totalQuantity) {
        return couponTemplateRepository.save(new CouponTemplateModel(name, type, value, minOrderAmount, expiredAt, totalQuantity));
    }

    public CouponTemplateModel updateTemplate(Long couponTemplateId, String name, CouponType type, BigDecimal value,
                                              BigDecimal minOrderAmount, ZonedDateTime expiredAt, int totalQuantity) {
        CouponTemplateModel template = getById(couponTemplateId);
        template.update(name, type, value, minOrderAmount, expiredAt, totalQuantity);
        return couponTemplateRepository.save(template);
    }

    public void deleteTemplate(Long couponTemplateId) {
        CouponTemplateModel template = getById(couponTemplateId);
        template.delete();
        couponTemplateRepository.save(template);
    }

    // 원자적 UPDATE 단독 호출이라 JPA 영속성 컨텍스트를 거치지 않는다 - 호출자(컨슈머 처리 트랜잭션)에 합류해 커밋된다.
    @Transactional
    public boolean reserveQuantity(Long couponTemplateId) {
        return couponTemplateRepository.increaseIssuedQuantityIfAvailable(couponTemplateId);
    }

}
