package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductSearchCondition;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductService productService;

    public ProductInfo createProduct(String name, String description, Long price, Integer stock, Long brandId) {
        ProductModel product = productService.createProduct(name, description, price, stock, brandId);
        return ProductInfo.from(product);
    }

    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getProduct(id);
        return ProductInfo.from(product);
    }

    public List<ProductInfo> getAllProducts() {
        List<ProductModel> products = productService.getAllProducts();
        return products.stream()
            .map(ProductInfo::from)
            .toList();
    }

    public ProductPageInfo searchProducts(ProductSearchCondition condition) {
        return ProductPageInfo.from(productService.searchProducts(condition));
    }

    public ProductInfo updateProduct(
        Long id, String name, String description, Long price, Integer stock, Long brandId) {
        ProductModel product = productService.updateProduct(id, name, description, price, stock, brandId);
        return ProductInfo.from(product);
    }

    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }
}
