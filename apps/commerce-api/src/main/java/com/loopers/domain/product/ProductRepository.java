package com.loopers.domain.product;

import java.util.Optional;
import java.util.List;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> find(Long id);
    /** 미삭제 상품을 id 배치로 조회 (커서 페이지의 N+1 단건 조회 제거용). */
    List<ProductModel> findAllByIds(List<Long> ids);
    List<ProductModel> findAll(Long brandId, ProductSortType sort, int page, int size);

    /** 재고 차감용 비관적 락 조회. id 오름차순으로 잠가 다중 상품 주문 간 교차 데드락을 방지한다. */
    List<ProductModel> findAllForUpdate(List<Long> ids);
}
