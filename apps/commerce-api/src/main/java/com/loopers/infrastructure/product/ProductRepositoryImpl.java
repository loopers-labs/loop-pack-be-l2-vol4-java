package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductFilter;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    public Optional<ProductModel> findById(Long id) {
        return productJpaRepository.findById(id);
    }

    @Override
    public Optional<ProductModel> findByIdForUpdate(Long id) {
        return productJpaRepository.findByIdForUpdate(id);
    }

    @Override
    public Page<ProductModel> findAll(ProductFilter filter, ProductSort sort, PageRequest pageRequest) {
        if (sort == ProductSort.LIKES_DESC) {
            Pageable unsorted = PageRequest.of(pageRequest.getPageNumber(), pageRequest.getPageSize());
            return productJpaRepository.findAllWithFilterOrderByLikeCountDesc(
                filter.brandId(), filter.minPrice(), filter.maxPrice(), filter.inStock(), unsorted);
        }

        PageRequest sorted = PageRequest.of(pageRequest.getPageNumber(), pageRequest.getPageSize(), toSort(sort));
        return productJpaRepository.findAllWithFilter(
            filter.brandId(), filter.minPrice(), filter.maxPrice(), filter.inStock(), sorted);
    }

    @Override
    public List<ProductModel> findAllByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandId(brandId);
    }

    @Override
    public List<ProductModel> findAllByIds(List<Long> ids) {
        return productJpaRepository.findAllById(ids);
    }

    private Sort toSort(ProductSort sort) {
        return switch (sort) {
            case PRICE_ASC -> Sort.by(Sort.Order.asc("price"));
            case LATEST -> Sort.by(Sort.Order.desc("createdAt"));
            case LIKES_DESC -> throw new IllegalStateException("LIKES_DESC는 별도 쿼리로 처리됩니다.");
        };
    }
}
