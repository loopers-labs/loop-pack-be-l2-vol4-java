package com.loopers.infrastructure.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.product.projection.ProductAdminView;
import com.loopers.domain.product.projection.ProductDetail;
import com.loopers.domain.product.projection.ProductSummary;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public ProductModel save(ProductModel product) {
        return productJpaRepository.save(product);
    }

    @Override
    public ProductModel getActiveById(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다."));
    }

    @Override
    public ProductModel getActiveByIdForUpdate(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNullForUpdate(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다."));
    }

    @Override
    public Optional<ProductModel> findActiveById(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<ProductModel> findActiveByBrandId(Long brandId) {
        return productJpaRepository.findByBrandIdAndDeletedAtIsNull(brandId);
    }

    @Override
    public int incrementLikeCount(Long id) {
        return productJpaRepository.incrementLikeCount(id);
    }

    @Override
    public int decrementLikeCount(Long id) {
        return productJpaRepository.decrementLikeCount(id);
    }

    @Override
    public Page<ProductSummary> findActiveSummaries(Long brandId, ProductSortType sort, int page, int size) {
        return switch (sort) {
            case LATEST -> productJpaRepository.findActiveSummaries(brandId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
            case PRICE_ASC -> productJpaRepository.findActiveSummaries(brandId, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "price.value")));
            case LIKES_DESC -> productJpaRepository.findActiveSummariesOrderByLikeCount(brandId, PageRequest.of(page, size));
        };
    }

    @Override
    public ProductDetail getActiveDetailById(Long id) {
        return productJpaRepository.findActiveDetailById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다."));
    }

    @Override
    public Page<ProductAdminView> findActiveAdminViews(Long brandId, int page, int size) {
        return productJpaRepository.findActiveAdminViews(brandId, PageRequest.of(page, size));
    }

    @Override
    public ProductAdminView getActiveAdminViewById(Long id) {
        return productJpaRepository.findActiveAdminViewById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품이 존재하지 않습니다."));
    }
}
