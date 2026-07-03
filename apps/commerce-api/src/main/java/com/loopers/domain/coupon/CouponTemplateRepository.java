package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CouponTemplateRepository {

    Optional<CouponTemplateModel> findById(Long id);

    List<CouponTemplateModel> findAllByIds(Set<Long> ids);

    Page<CouponTemplateModel> findAll(Pageable pageable);

    CouponTemplateModel save(CouponTemplateModel couponTemplate);

    // 동시성 처리: issuedQuantity < totalQuantity 조건을 만족할 때만 issuedQuantity를 원자적으로 +1 한다.
    // 애플리케이션 레벨 락 없이 단일 UPDATE로 선착순 수량 초과 발급을 막는다(true=예약 성공, false=수량 소진/템플릿 없음).
    boolean increaseIssuedQuantityIfAvailable(Long couponTemplateId);
}
