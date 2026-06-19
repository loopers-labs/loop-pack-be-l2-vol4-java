package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);

    Optional<Product> find(Long id);

    /**
     * 비관적 쓰기 락(SELECT ... FOR UPDATE)으로 상품을 조회한다.
     * 재고 차감처럼 high-contention 한 write 경로에서만 사용한다.
     * 호출자는 트랜잭션 내부에 있어야 한다.
     */
    Optional<Product> findForUpdate(Long id);

    List<Product> findAll();

    /**
     * 브랜드 필터 + 정렬 + 페이징으로 상품을 조회한다.
     * LATEST / PRICE_ASC 는 저장소에서 정렬한다. LIKES_DESC 는 좋아요 집계가 필요하므로
     * 조합 단계(Application/Domain Service)에서 정렬한다.
     */
    List<Product> findAll(Long brandId, ProductSortType sort, int page, int size);

    List<Product> findAllByIds(List<Long> ids);

    boolean existsById(Long id);

    void delete(Long id);
}
