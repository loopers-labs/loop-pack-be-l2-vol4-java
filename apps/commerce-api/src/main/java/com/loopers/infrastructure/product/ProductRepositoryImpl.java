package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSort;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public Product save(Product product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<Product> findActiveById(Long productId) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(productId);
    }

    @Override
    public List<Product> findActiveAllByIds(Collection<Long> productIds) {
        return productJpaRepository.findByIdInAndDeletedAtIsNull(productIds);
    }

    @Override
    public PageResult<Product> findActiveAll(PageQuery query, Long brandId) {
        Pageable pageable = PageRequest.of(query.page(), query.size(), Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
        ));
        Page<Product> page = brandId == null
            ? productJpaRepository.findByDeletedAtIsNull(pageable)
            : productJpaRepository.findByBrandIdAndDeletedAtIsNull(brandId, pageable);
        return new PageResult<>(
            page.getContent(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize(),
            page.isFirst(),
            page.isLast()
        );
    }

    @Override
    public PageResult<Product> findVisibleAll(PageQuery query, Long brandId, ProductSort sort) {
        Page<Product> page = findVisiblePage(query, brandId, sort);
        return toPageResult(page);
    }

    @Override
    public PageResult<Product> findVisibleLikedAllByUserId(Long userId, PageQuery query) {
        Page<Product> page = productJpaRepository.findVisibleLikedAllByUserId(
            userId,
            PageRequest.of(query.page(), query.size())
        );
        return toPageResult(page);
    }

    private Page<Product> findVisiblePage(PageQuery query, Long brandId, ProductSort sort) {
        if (sort == ProductSort.LIKES_DESC) {
            return productJpaRepository.findVisibleAllOrderByLikes(
                brandId,
                PageRequest.of(query.page(), query.size())
            );
        }
        return productJpaRepository.findVisibleAll(
            brandId,
            PageRequest.of(query.page(), query.size(), toSort(sort))
        );
    }

    private Sort toSort(ProductSort sort) {
        return switch (sort) {
            case PRICE_ASC -> Sort.by(
                Sort.Order.asc("price"),
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
            );
            case LATEST, LIKES_DESC -> Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
            );
        };
    }

    private PageResult<Product> toPageResult(Page<Product> page) {
        return new PageResult<>(
            page.getContent(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize(),
            page.isFirst(),
            page.isLast()
        );
    }
}
