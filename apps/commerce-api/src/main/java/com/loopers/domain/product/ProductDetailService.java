package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductDetailService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    @Transactional(readOnly = true)
    public ProductDetail getDetail(Long productId) {
        ProductModel product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        BrandModel brand = brandRepository.findById(product.getBrandId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + product.getBrandId() + "] 브랜드를 찾을 수 없습니다."));
        return new ProductDetail(product, brand);
    }
}
