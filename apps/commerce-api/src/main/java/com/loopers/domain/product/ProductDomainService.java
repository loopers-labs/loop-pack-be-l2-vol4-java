package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductDomainService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final LikeRepository likeRepository;

    @Transactional(readOnly = true)
    public List<ProductWithDetail> getProductsWithDetail(Long brandId, ProductSortType sort) {
        List<ProductModel> products = productRepository.findAllActive(brandId);

        return products.stream()
                .map(product -> toDetail(product, false))
                .filter(Objects::nonNull)
                .sorted(comparatorOf(sort))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductWithDetail getProductWithDetail(Long productId) {
        ProductModel product = productRepository.find(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));

        ProductWithDetail detail = toDetail(product, true);
        if (detail == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "계약 중지되었거나 삭제된 브랜드의 상품은 조회할 수 없습니다.");
        }
        return detail;
    }

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

    private ProductWithDetail toDetail(ProductModel product, boolean throwIfSuspended) {
        Optional<BrandModel> brandOpt = brandRepository.find(product.getBrandId());
        if (brandOpt.isEmpty()) {
            return null;
        }
        BrandModel brand = brandOpt.get();
        if (brand.isSuspended()) {
            if (throwIfSuspended) {
                return null;
            }
            return null;
        }
        long likeCount = likeRepository.countActiveByProductId(product.getId());
        return new ProductWithDetail(product, brand, likeCount);
    }

    private Comparator<ProductWithDetail> comparatorOf(ProductSortType sort) {
        return switch (sort) {
            case PRICE_ASC -> Comparator.comparingLong(d -> d.product().getPrice());
            case LIKES_DESC -> Comparator.comparingLong(ProductWithDetail::likeCount).reversed();
            case LATEST -> Comparator.comparing((ProductWithDetail d) -> d.product().getCreatedAt()).reversed();
        };
    }
}