package com.loopers.application.product;

import com.loopers.application.support.PageResult;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductService productService;

    public ProductInfo getProduct(Long id) {
        Product product = productService.getProduct(id);
        return ProductInfo.from(product);
    }

    public PageResult<ProductInfo> getProducts(Long brandId, String sort, int page, int size) {
        List<ProductInfo> items = productService.getProducts(brandId, sort, page, size).stream()
            .map(ProductInfo::from)
            .toList();
        long totalElements = productService.countProducts(brandId);
        return new PageResult<>(items, page, size, totalElements);
    }
}
