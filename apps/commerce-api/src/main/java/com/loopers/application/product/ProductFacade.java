package com.loopers.application.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductService productService;

    public ProductInfo createProduct(String name, String description, Long price, Integer stock) {
        Product product = productService.createProduct(name, description, price, stock);
        return ProductInfo.from(product);
    }

    public ProductInfo getProduct(Long id) {
        Product product = productService.getProduct(id);
        return ProductInfo.from(product);
    }

    public List<ProductInfo> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        return products.stream()
            .map(ProductInfo::from)
            .toList();
    }

    public ProductInfo updateProduct(Long id, String name, String description, Long price, Integer stock) {
        Product product = productService.updateProduct(id, name, description, price, stock);
        return ProductInfo.from(product);
    }

    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }
}
