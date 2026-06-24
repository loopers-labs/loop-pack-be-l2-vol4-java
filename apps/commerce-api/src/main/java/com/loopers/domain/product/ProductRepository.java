package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> findById(Long id);
    List<ProductModel> findAllByIds(List<Long> ids);
    List<ProductModel> findAll(Long brandId, String sort, int page, int size);
    List<ProductModel> findAllByBrandId(Long brandId);

    /**
     * 브랜드에 속한 모든 상품을 단일 UPDATE 쿼리로 soft delete 처리한다.
     * BrandApplicationService.deleteBrand() 에서 N+1 없이 연관 상품을 일괄 삭제하기 위해 사용한다.
     */
    void softDeleteAllByBrandId(Long brandId);

    /** 좋아요 수 비정규화 — 조건부 원자 증가/감소. */
    void increaseLikeCount(Long productId);
    void decreaseLikeCount(Long productId);

    /** 모든 상품의 like_count 를 실제 likes 집계로 재계산(drift 보정). @return 갱신 행 수 */
    int resyncAllLikeCounts();
}
