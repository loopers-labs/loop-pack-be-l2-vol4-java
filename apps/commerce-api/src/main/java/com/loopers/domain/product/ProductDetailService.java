package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 상세 조회 시 상품과 브랜드 정보를 조합하는 도메인 서비스.
 * 상태를 갖지 않고 ProductService/BrandService 의 협력만 담당한다.
 */
@RequiredArgsConstructor
@Component
public class ProductDetailService {

    private final ProductService productService;
    private final BrandService brandService;

    @Transactional(readOnly = true)
    public ProductDetail getProductDetail(Long productId) {
        ProductModel product = productService.getProduct(productId);
        BrandModel brand = brandService.getBrand(product.getBrandId());
        return new ProductDetail(product, brand);
    }
}
