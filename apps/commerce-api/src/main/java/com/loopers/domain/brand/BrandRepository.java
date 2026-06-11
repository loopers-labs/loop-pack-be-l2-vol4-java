package com.loopers.domain.brand;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface BrandRepository {
    BrandModel save(BrandModel brand);

    /** 어드민용 — 삭제된 브랜드 포함 단건 조회 */
    Optional<BrandModel> find(UUID id);

    /** 고객용 — 활성(deletedAt IS NULL) 브랜드만 단건 조회 */
    Optional<BrandModel> findActive(UUID id);

    /** 어드민 목록 — 삭제된 브랜드 포함 */
    Page<BrandModel> findAll(Pageable pageable);
}
