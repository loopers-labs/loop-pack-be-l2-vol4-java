package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductReader;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductReaderImpl implements ProductReader {

    private final ProductService productService;

    @Override
    public ProductModel getProduct(Long productId) {
        return productService.getProduct(productId);
    }

    @Override
    public boolean existsProduct(Long productId) {
        try {
            productService.getProduct(productId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
