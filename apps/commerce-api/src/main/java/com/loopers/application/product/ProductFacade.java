package com.loopers.application.product;

import com.loopers.domain.product.ProductEntity;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductService productService;

    public ProductInfo createProduct(Long brandId, String name, String description, Long price) {
        ProductEntity product = productService.createProduct(brandId, name, description, price);
        return ProductInfo.from(product);
    }

    public ProductInfo getProduct(Long id) {
        ProductEntity product = productService.getProduct(id);
        return ProductInfo.from(product);
    }

    public Page<ProductInfo> getAllProducts(Long brandId, Pageable pageable) {
        return productService.getAllProducts(brandId, pageable)
                .map(ProductInfo::from);
    }

    public ProductInfo updateProduct(Long id, String name, String description, Long price) {
        ProductEntity product = productService.updateProduct(id, name, description, price);
        return ProductInfo.from(product);
    }

    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }
}
