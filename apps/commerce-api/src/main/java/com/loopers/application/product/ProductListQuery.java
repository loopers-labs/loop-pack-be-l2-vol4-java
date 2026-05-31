package com.loopers.application.product;

import com.loopers.domain.product.ProductSort;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;

public interface ProductListQuery {
    PageResult<ProductListInfo> findVisibleProducts(PageQuery query, Long brandId, ProductSort sort);
}
