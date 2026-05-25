package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;

import java.util.List;
import java.util.Map;

public final class ProductDetailAssembler {

    private ProductDetailAssembler() {}

    public static ProductDetail assemble(ProductModel product, BrandModel brand) {
        return new ProductDetail(
            product.getId(),
            brand.getName(),
            product.getName(),
            product.getPrice(),
            product.getLikeCount()
        );
    }

    public static List<ProductDetail> assembleAll(List<ProductModel> products, Map<Long, BrandModel> brandMap) {
        return products.stream()
                .map(product -> assemble(product, brandMap.get(product.getBrandId())))
                .toList();
    }
}
