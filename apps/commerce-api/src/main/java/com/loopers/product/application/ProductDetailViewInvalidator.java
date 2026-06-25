package com.loopers.product.application;

import java.util.Collection;

public interface ProductDetailViewInvalidator {

    void invalidate(Long productId);

    void invalidateAll(Collection<Long> productIds);
}
