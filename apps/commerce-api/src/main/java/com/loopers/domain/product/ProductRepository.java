package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {
    ProductModel save(ProductModel product);

    /** 어드민용 — 삭제된 상품 포함 단건 조회 */
    Optional<ProductModel> find(UUID id);

    /** 고객용 — 활성(deletedAt IS NULL) 상품만 단건 조회 */
    Optional<ProductModel> findActive(UUID id);

    /** 어드민 목록 — 삭제된 상품 포함 */
    Page<ProductModel> findAll(Pageable pageable);

    /** 고객 목록 — 활성 상품만 */
    Page<ProductModel> findAllActive(Pageable pageable);

    /** 브랜드 소프트딜리트 시 cascade 처리용 — 브랜드 산하 전체 상품 조회 */
    List<ProductModel> findAllByBrandId(UUID brandId);

    /** 고객 목록 — 브랜드 필터 + 활성 상품만 */
    Page<ProductModel> findAllActiveByBrandId(UUID brandId, Pageable pageable);

    /** 어드민 목록 — 브랜드 필터 (삭제 포함) */
    Page<ProductModel> findAllByBrandIdPaged(UUID brandId, Pageable pageable);
}
