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

    PageResult<Product> findAll(ProductCommand.Search search);

    int bulkSoftDeleteByBrandId(Long brandId);
}
