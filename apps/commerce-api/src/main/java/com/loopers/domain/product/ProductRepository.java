package com.loopers.domain.product;

import com.loopers.domain.common.PageResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    void update(Product product);

    Optional<Product> find(Long id);

    List<Product> findAllByIds(Collection<Long> ids);

    /** 재고를 원자적으로 차감한다. 반환값은 영향받은 행 수 — 0 이면 재고 부족(또는 상품 없음). */
    int decreaseStock(Long productId, int amount);

    /** 좋아요 수를 원자적으로 1 증가시킨다. */
    int increaseLikeCount(Long productId);

    /** 좋아요 수를 원자적으로 1 감소시킨다(0 미만 방지). */
    int decreaseLikeCount(Long productId);

    PageResult<Product> findAll(ProductCommand.Search search);

    int bulkSoftDeleteByBrandId(Long brandId);
}
