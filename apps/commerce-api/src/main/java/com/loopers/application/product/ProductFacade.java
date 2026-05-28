package com.loopers.application.product;

import com.loopers.domain.product.model.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductApplicationService productApplicationService;

    public ProductInfo getProduct(Long productId) {
        return productApplicationService.getProduct(productId);
    }

    public Page<ProductInfo> getProducts(Long brandId, String sort, int page, int size) {
        return productApplicationService.getProducts(brandId, ProductSort.from(sort), page, size);
    }

    public ProductInfo createProduct(Long brandId, String name, String description, Long price, int initialQuantity) {
        Product product = productApplicationService.createProduct(brandId, name, description, price, initialQuantity);
        return ProductInfo.from(product);
    }
}
