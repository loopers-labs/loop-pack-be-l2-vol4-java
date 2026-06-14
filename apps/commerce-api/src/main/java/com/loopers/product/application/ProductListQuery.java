package com.loopers.product.application;

import com.loopers.product.domain.ProductSort;
import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;

public interface ProductListQuery {
    PageResult<ProductListInfo> findVisibleProducts(PageQuery query, Long brandId, ProductSort sort);
}
