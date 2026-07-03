package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 상세 조회 시 Product + Brand 정보를 조합하는 도메인 서비스.
 * 한 도메인에 귀속시키기 부자연스러운 협력 로직(두 Aggregate 의 조합)을 담당한다.
 */
@RequiredArgsConstructor
@Component
public class ProductDetailService {

    private final ProductService productService;
    private final BrandService brandService;

    @Transactional(readOnly = true)
    public ProductDetail getDetail(Long productId) {
        ProductModel product = productService.getProduct(productId);
        BrandModel brand = brandService.getBrand(product.getBrandId());
        return new ProductDetail(product, brand);
    }

    public record ProductDetail(ProductModel product, BrandModel brand) {}
}
