package com.loopers.product.infrastructure;

import com.loopers.product.domain.ProductModel;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductSummaryModel;
import com.loopers.product.domain.SortCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public ProductModel save(ProductModel product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<ProductModel> find(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<ProductSummaryModel> findAll(SortCondition sort, Long brandId, boolean inStock, int page, int size) {
        Sort springSort = switch (sort) {
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price");
            case LIKES_DESC -> Sort.by(Sort.Direction.DESC, "likeCount")
                .and(Sort.by(Sort.Direction.DESC, "createdAt"));
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
        PageRequest pageRequest = PageRequest.of(page, size, springSort);
        if (inStock) {
            return brandId == null
                ? productJpaRepository.findAllActiveInStock(pageRequest)
                : productJpaRepository.findAllActiveByBrandIdInStock(brandId, pageRequest);
        }
        return brandId == null
            ? productJpaRepository.findAllActive(pageRequest)
            : productJpaRepository.findAllActiveByBrandId(brandId, pageRequest);
    }

    @Override
    public List<ProductModel> findAllByIds(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return productJpaRepository.findAllByIds(ids);
    }

    @Override
    public List<ProductModel> findAllByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandId(brandId);
    }

    @Override
    public void incrementLikeCount(Long productId) {
        productJpaRepository.incrementLikeCount(productId);
    }

    @Override
    public void decrementLikeCount(Long productId) {
        productJpaRepository.decrementLikeCount(productId);
    }
}
