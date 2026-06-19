package com.loopers.domain.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 상품 표시(조회)를 위한 도메인 서비스.
 * 서로 다른 도메인(Product, Brand, Like)을 조합해 상품 상세/목록에 필요한 정보를 구성한다.
 * 상태를 갖지 않고, 도메인 객체들의 협력만 책임진다.
 */
@RequiredArgsConstructor
@Component
public class ProductDisplayService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final LikeRepository likeRepository;

    @Transactional(readOnly = true)
    public ProductDetail getProductDetail(Long productId) {
        Product product = productRepository.find(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        return toDetail(product);
    }

    @Transactional(readOnly = true)
    public List<ProductDetail> getProductDetails(List<Product> products) {
        return products.stream()
            .map(this::toDetail)
            .toList();
    }

    private ProductDetail toDetail(Product product) {
        Brand brand = brandRepository.find(product.getBrandId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[brandId = " + product.getBrandId() + "] 브랜드를 찾을 수 없습니다."));
        long likeCount = likeRepository.countByProductId(product.getId());
        return new ProductDetail(product, brand, likeCount);
    }
}
