package com.loopers.domain.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 상품 표시(조회)를 위한 도메인 서비스.
 * 서로 다른 도메인(Product, Brand, ProductLikeStat) 을 조합해 상품 상세/목록에 필요한 정보를 구성한다.
 * 상태를 갖지 않고, 도메인 객체들의 협력만 책임진다.
 *
 * Week 5: 좋아요 수는 ProductLikeStat(read-model)에서 조회한다. 목록 조회 시 stat 을
 *         ID-IN 으로 한 번에 가져와 N+1 을 제거한다 (직전엔 매 상품마다 COUNT(*) 호출).
 */
@RequiredArgsConstructor
@Component
public class ProductDisplayService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final ProductLikeStatRepository productLikeStatRepository;

    @Transactional(readOnly = true)
    public ProductDetail getProductDetail(Long productId) {
        Product product = productRepository.find(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        long likeCount = likeCountOf(productId);
        return toDetail(product, likeCount);
    }

    @Transactional(readOnly = true)
    public List<ProductDetail> getProductDetails(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }
        // 좋아요 수: 한 번의 IN 쿼리로 모두 가져와 매핑 → N+1 제거
        List<Long> productIds = products.stream().map(Product::getId).toList();
        Map<Long, Long> likeCountByProductId = productLikeStatRepository.findAllByProductIdIn(productIds).stream()
            .collect(Collectors.toMap(ProductLikeStat::getProductId, ProductLikeStat::getLikeCount));

        return products.stream()
            .map(product -> toDetail(product, likeCountByProductId.getOrDefault(product.getId(), 0L)))
            .toList();
    }

    private long likeCountOf(Long productId) {
        return productLikeStatRepository.find(productId)
            .map(ProductLikeStat::getLikeCount)
            .orElse(0L);
    }

    private ProductDetail toDetail(Product product, long likeCount) {
        Brand brand = brandRepository.find(product.getBrandId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[brandId = " + product.getBrandId() + "] 브랜드를 찾을 수 없습니다."));
        return new ProductDetail(product, brand, likeCount);
    }
}
