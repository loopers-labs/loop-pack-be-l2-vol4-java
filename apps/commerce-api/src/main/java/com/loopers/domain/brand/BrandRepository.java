package com.loopers.domain.brand;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BrandRepository {
    BrandModel save(BrandModel brand);
    Optional<BrandModel> find(Long id);

    /** 여러 브랜드를 id로 일괄 조회 — 상품 목록의 brandName batch 조합용 (N+1 회피). */
    List<BrandModel> findByIds(Collection<Long> ids);
}
