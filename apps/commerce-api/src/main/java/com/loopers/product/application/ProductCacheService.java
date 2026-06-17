package com.loopers.product.application;

import com.loopers.brand.domain.BrandModel;
import com.loopers.brand.domain.BrandRepository;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductCacheService {

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    // stock을 캐시에서 분리 — 상세 조회 시 재고는 항상 별도로 실시간 조회
    @Cacheable(cacheNames = "product", key = "#productId")
    @Transactional(readOnly = true)
    public ProductInfo getProductWithoutStock(Long productId) {
        ProductModel product = productService.getOrThrow(productRepository.find(productId));
        Optional<BrandModel> brand = product.getBrandId() != null
            ? brandRepository.find(product.getBrandId())
            : Optional.empty();
        return ProductInfo.from(product, productService.resolveBrandName(brand), null);
    }
}
