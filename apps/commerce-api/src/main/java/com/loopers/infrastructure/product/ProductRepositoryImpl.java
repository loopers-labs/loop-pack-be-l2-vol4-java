package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.SortOption;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public ProductModel save(ProductModel product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<ProductModel> findById(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<ProductModel> findAllByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }

    @Override
    public boolean existsById(Long id) {
        return productJpaRepository.existsByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Page<ProductModel> search(Long brandId, SortOption sort, Pageable pageable) {
        Pageable sortedPageable = PageRequest.of(
            pageable.getPageNumber(), pageable.getPageSize(), toSort(sort)
        );

        // 옵티마이저가 인덱스를 명확히 타도록 쿼리를 분기한다. nullable 조건이 늘어나면 메서드가 2^N으로 증가하므로, QueryDSL 적용을 고려한다.
        return brandId == null
            ? productJpaRepository.findAllByDeletedAtIsNull(sortedPageable)
            : productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId, sortedPageable);
    }

    @Override
    public long countByBrandId(Long brandId) {
        return productJpaRepository.countByBrandIdAndDeletedAtIsNull(brandId);
    }

    @Override
    public Map<Long, Long> countByBrandIds(Collection<Long> brandIds) {
        if (brandIds.isEmpty()) {
            return Map.of();
        }
        return productJpaRepository.countGroupByBrandIdAndDeletedAtIsNull(brandIds).stream()
            .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    /**
     * SortOption(도메인) → Spring Data Sort(인프라) 매핑.
     * id 컬럼을 보조 정렬 키로 붙여 페이지 사이 중복/누락을 방지한다.
     */
    private Sort toSort(SortOption option) {
        return switch (option) {
            case LATEST -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            case PRICE_ASC -> Sort.by(Sort.Order.asc("price"), Sort.Order.asc("id"));
            case LIKES_DESC -> Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("id"));
        };
    }
}
