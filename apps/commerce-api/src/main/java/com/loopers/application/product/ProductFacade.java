package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductService productService;

    public ProductInfo createProduct(String name, String description, Long price, Integer stock) {
        ProductModel product = productService.createProduct(name, description, price, stock);
        return ProductInfo.from(product);
    }

    public ProductInfo getProduct(UUID id) {
        ProductModel product = productService.getProduct(id);
        return ProductInfo.from(product);
    }

    public List<ProductInfo> getAllProducts() {
        List<ProductModel> products = productService.getAllProducts();
        return products.stream()
            .map(ProductInfo::from)
            .toList();
    }

    public ProductInfo updateProduct(UUID id, String name, String description, Long price, Integer stock) {
        ProductModel product = productService.updateProduct(id, name, description, price, stock);
        return ProductInfo.from(product);
    }

    public void deleteProduct(UUID id) {
        productService.deleteProduct(id);
    }
}
