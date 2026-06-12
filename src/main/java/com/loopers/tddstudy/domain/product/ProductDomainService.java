package com.loopers.tddstudy.domain.product;

import com.loopers.tddstudy.domain.brand.Brand;
import com.loopers.tddstudy.domain.brand.BrandRepository;

public class ProductDomainService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    public ProductDomainService(ProductRepository productRepository,
                                BrandRepository brandRepository) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
    }

    public ProductDetail getDetail(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        Brand brand = brandRepository.findById(product.getBrandId())
                .orElseThrow(() -> new IllegalArgumentException("브랜드를 찾을 수 없습니다."));

        if (brand.isDeleted()) {
            throw new IllegalArgumentException("브랜드를 찾을 수 없습니다.");
        }

        return new ProductDetail(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock(),
                product.getLikeCount(),
                product.getStatus(),
                brand.getId(),
                brand.getName()
        );
    }
}
