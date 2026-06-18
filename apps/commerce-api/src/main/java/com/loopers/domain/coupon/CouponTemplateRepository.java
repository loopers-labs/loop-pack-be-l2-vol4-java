package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface CouponTemplateRepository {
    /** 저장 + 즉시 flush — unique 충돌을 호출 지점에서 DataIntegrityViolationException으로 감지 */
    CouponTemplateModel saveAndFlush(CouponTemplateModel template);

    /** 어드민용 — 삭제된 템플릿 포함 단건 조회 */
    Optional<CouponTemplateModel> find(UUID id);

    /** 발급용 — 활성(deletedAt IS NULL) 템플릿만 단건 조회 */
    Optional<CouponTemplateModel> findActive(UUID id);

    /** 어드민 목록 — 삭제된 템플릿 포함 */
    Page<CouponTemplateModel> findAll(Pageable pageable);
}
