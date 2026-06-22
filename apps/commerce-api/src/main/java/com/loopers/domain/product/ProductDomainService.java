package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import org.springframework.stereotype.Component;

@Component
public class ProductDomainService {

    public ProductDetail combineWithBrand(ProductModel product, BrandModel brand, int likeCount, int stockQuantity) {
        return new ProductDetail(
            product.getId(),
            product.getName(),
            product.getPrice(),
            brand.getId(),
            brand.getName(),
            likeCount,
            stockQuantity
        );
    }
}
