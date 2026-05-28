package com.loopers.application.product;

import com.loopers.domain.product.ProductDetailView;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductService productService;

    @Transactional
    public ProductInfo createProduct(Long brandId, String name, String description, Long price, Integer stock) {
        return ProductInfo.from(productService.createProduct(brandId, name, description, price, stock));
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long id) {
        return ProductInfo.from(productService.getProduct(id));
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getAllProducts() {
        return getAllProducts(null);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getAllProducts(String sort) {
        return getAllProducts(null, sort, null, null);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getAllProducts(Long brandId, String sort, Integer page, Integer size) {
        return productService.getAllProducts(brandId, sort, page, size).stream()
            .map(ProductInfo::from)
            .toList();
    }

    @Transactional
    public ProductInfo updateProduct(Long id, String name, String description, Long price, Integer stock) {
        ProductDetailView productDetailView = productService.updateProduct(id, name, description, price, stock);
        return ProductInfo.from(productDetailView);
    }

    @Transactional
    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }
}
