package com.loopers.application.product;

import com.loopers.domain.product.model.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductApplicationService productApplicationService;

    public ProductInfo createProduct(Long brandId, String name, String description, Long price, int initialQuantity) {
        Product product = productApplicationService.createProduct(brandId, name, description, price, initialQuantity);
        return ProductInfo.from(product);
    }
}
