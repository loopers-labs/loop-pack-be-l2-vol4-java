package com.loopers.product.application;

import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductReader {

    private final ProductRepository productRepository;

    public void ensureActiveExists(Long productId) {
        if (!productRepository.existsActiveById(productId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.");
        }
    }

    public ProductInfo getInfo(Long productId) {
        Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        return new ProductInfo(product.getName(), product.getBrandId(), product.getPrice().value());
    }
}
