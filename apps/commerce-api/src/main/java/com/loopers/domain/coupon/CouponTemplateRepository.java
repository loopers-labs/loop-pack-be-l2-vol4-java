package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface CouponTemplateRepository {
    CouponTemplateModel save(CouponTemplateModel template);

    /** 어드민용 — 삭제된 템플릿 포함 단건 조회 */
    Optional<CouponTemplateModel> find(UUID id);

    /** 발급용 — 활성(deletedAt IS NULL) 템플릿만 단건 조회 */
    Optional<CouponTemplateModel> findActive(UUID id);

    /** 쿠폰명 중복 검사 — 삭제된 템플릿명도 포함(재등록 영구 차단) */
    boolean existsByName(String name);

    /** 어드민 목록 — 삭제된 템플릿 포함 */
    Page<CouponTemplateModel> findAll(Pageable pageable);
}
