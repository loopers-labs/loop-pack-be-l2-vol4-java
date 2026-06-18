package com.loopers.product.application;

import java.util.Optional;

public interface ProductDetailQuery {

    Optional<ProductDetailInfo> findVisibleProduct(Long productId);
}
