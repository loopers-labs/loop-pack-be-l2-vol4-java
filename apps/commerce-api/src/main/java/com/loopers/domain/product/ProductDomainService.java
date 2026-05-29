package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import org.springframework.stereotype.Component;

@Component
public class ProductDomainService {

    public ProductDetail compose(ProductModel product, BrandModel brand, long likeCount) {
        return new ProductDetail(
            product.getId(),
            product.getBrandId(),
            brand != null ? brand.getName() : null,
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStock(),
            likeCount
        );
    }
}
