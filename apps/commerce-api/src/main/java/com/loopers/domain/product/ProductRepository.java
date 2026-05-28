package com.loopers.domain.product;

import com.loopers.domain.common.PageResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    void update(Product product);

    void updateAll(List<Product> products);

    Optional<Product> find(Long id);

    List<Product> findAllByIds(Collection<Long> ids);

    PageResult<Product> findAll(ProductCommand.Search search);

    int bulkSoftDeleteByBrandId(Long brandId);
}
