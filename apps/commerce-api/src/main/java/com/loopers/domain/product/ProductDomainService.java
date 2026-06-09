package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductDomainService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductModel deductStock(Long productId, int quantity) {
        ProductModel product = productRepository.findWithLock(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        product.deductStock(quantity);
        return product;
    }

    @Transactional
    public ProductModel restoreStock(Long productId, int quantity) {
        ProductModel product = productRepository.find(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        product.restoreStock(quantity);
        return product;
    }
}
