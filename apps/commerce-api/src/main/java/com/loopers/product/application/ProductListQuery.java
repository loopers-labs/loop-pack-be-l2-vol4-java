package com.loopers.product.application;

import com.loopers.product.domain.ProductSort;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;

public interface ProductListQuery {
    PageResult<ProductListInfo> findVisibleProducts(PageQuery query, Long brandId, ProductSort sort);
}
