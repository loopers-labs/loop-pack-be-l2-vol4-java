package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Domain Service.
 * Product 단독으로 해결되지 않는 "다른 도메인과의 조합" 책임을 담당한다.
 * 상태를 갖지 않으며, 도메인 객체들의 협력 흐름을 명시적으로 보여준다.
 */
@RequiredArgsConstructor
@Component
public class ProductDetailService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    /**
     * 상품 + 소속 브랜드를 함께 조회한다.
     * 둘 중 하나라도 없으면 NOT_FOUND.
     */
    public ProductWithBrand getProductWithBrand(Long productId) {
        ProductModel product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        BrandModel brand = brandRepository.findById(product.getBrandId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "[id = " + product.getBrandId() + "] 브랜드를 찾을 수 없습니다."));
        return new ProductWithBrand(product, brand);
    }

    public record ProductWithBrand(ProductModel product, BrandModel brand) {}
}
