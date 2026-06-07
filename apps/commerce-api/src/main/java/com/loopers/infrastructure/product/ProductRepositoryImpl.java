package com.loopers.infrastructure.product;

import com.loopers.domain.common.PageResult;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductCommand;
import com.loopers.domain.product.ProductRepository;
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
    public void update(Product product) {
        productJpaRepository.save(product);
    }

    @Override
    public Optional<Product> find(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<Product> findAllByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }

    @Override
    public int decreaseStock(Long productId, int amount) {
        return productJpaRepository.decreaseStock(productId, amount);
    }

    @Override
    public PageResult<Product> findAll(ProductCommand.Search search) {
        Page<Product> page = switch (search.sort()) {
            case LATEST -> findOrderByLatest(search);
            case PRICE_ASC -> findOrderByPriceAsc(search);
            case LIKES_DESC -> findOrderByLikesDesc(search);
        };
        return new PageResult<>(
                page.getContent(),
                search.page(),
                search.size(),
                page.hasNext(),
                page.getTotalElements()
        );
    }

    @Override
    public int bulkSoftDeleteByBrandId(Long brandId) {
        return productJpaRepository.bulkSoftDeleteByBrandId(brandId);
    }

    private Page<Product> findOrderByLatest(ProductCommand.Search s) {
        Pageable pageable = PageRequest.of(s.page(), s.size(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return s.brandId() == null
                ? productJpaRepository.findAllByDeletedAtIsNull(pageable)
                : productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(s.brandId(), pageable);
    }

    private Page<Product> findOrderByPriceAsc(ProductCommand.Search s) {
        Pageable pageable = PageRequest.of(s.page(), s.size(),
                Sort.by(Sort.Order.asc("price.amount"), Sort.Order.desc("id")));
        return s.brandId() == null
                ? productJpaRepository.findAllByDeletedAtIsNull(pageable)
                : productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(s.brandId(), pageable);
    }

    private Page<Product> findOrderByLikesDesc(ProductCommand.Search s) {
        Pageable pageable = PageRequest.of(s.page(), s.size());
        return s.brandId() == null
                ? productJpaRepository.findAllOrderByLikesDesc(pageable)
                : productJpaRepository.findAllByBrandIdOrderByLikesDesc(s.brandId(), pageable);
    }
}
