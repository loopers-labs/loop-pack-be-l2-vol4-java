package com.loopers.application.product;

import com.loopers.domain.product.SortOption;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductAdminFacade {

    private final ProductCompositionReader reader;

    public ProductAdminInfo getProduct(Long productId) {
        ProductWithDeps c = reader.getDetail(productId);
        return ProductAdminInfo.from(c.product(), c.brand(), c.stockQuantity());
    }

    public Page<ProductAdminInfo> search(Long brandId, SortOption sort, Pageable pageable) {
        return reader.search(brandId, sort, pageable)
            .map(c -> ProductAdminInfo.from(c.product(), c.brand(), c.stockQuantity()));
    }
}
