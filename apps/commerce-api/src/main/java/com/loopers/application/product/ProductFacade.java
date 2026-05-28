package com.loopers.application.product;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;

    public ProductInfo getProduct(Long id) {
        return ProductInfo.from(productService.getById(id));
    }
}
