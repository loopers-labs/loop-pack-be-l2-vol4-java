package com.loopers.application.product;

import com.loopers.domain.product.SortOption;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductCompositionReader reader;

    public ProductInfo getProductDetail(Long productId) {
        ProductWithDeps c = reader.getDetail(productId);
        return ProductInfo.from(c.product(), c.brand(), c.stockQuantity() > 0);
    }

    public Page<ProductInfo> search(Long brandId, SortOption sort, Pageable pageable) {
        return reader.search(brandId, sort, pageable)
            .map(c -> ProductInfo.from(c.product(), c.brand(), c.stockQuantity() > 0));
    }
}
