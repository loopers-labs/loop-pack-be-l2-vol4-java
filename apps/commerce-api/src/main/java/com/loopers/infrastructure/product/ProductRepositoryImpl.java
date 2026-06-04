package com.loopers.infrastructure.product;

import com.loopers.domain.common.PageResult;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductCommand;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final ProductMapper productMapper;

    @Override
    public Product save(Product product) {
        ProductJpaEntity entity = productMapper.toJpaEntity(product);
        ProductJpaEntity saved = productJpaRepository.save(entity);
        return productMapper.toDomain(saved);
    }

    @Override
    public void update(Product product) {
        ProductJpaEntity managed = productJpaRepository.findByIdAndDeletedAtIsNull(product.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        managed.update(product.getName(), product.getPrice().getAmount());
        managed.updateStock(product.getStock().getQuantity());
        if (product.isDeleted() && managed.getDeletedAt() == null) {
            managed.delete();
        }
    }

    @Override
    public void updateAll(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return;
        }
        List<Long> ids = products.stream().map(Product::getId).toList();
        Map<Long, ProductJpaEntity> managedById = productJpaRepository
                .findAllByIdInAndDeletedAtIsNull(ids).stream()
                .collect(Collectors.toMap(ProductJpaEntity::getId, Function.identity()));

        for (Product product : products) {
            ProductJpaEntity managed = managedById.get(product.getId());
            if (managed == null) {
                throw new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.");
            }
            managed.update(product.getName(), product.getPrice().getAmount());
            managed.updateStock(product.getStock().getQuantity());
            if (product.isDeleted() && managed.getDeletedAt() == null) {
                managed.delete();
            }
        }
    }

    @Override
    public Optional<Product> find(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(productMapper::toDomain);
    }

    @Override
    public List<Product> findAllByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids).stream()
                .map(productMapper::toDomain)
                .toList();
    }

    @Override
    public PageResult<Product> findAll(ProductCommand.Search search) {
        Page<ProductJpaEntity> page = switch (search.sort()) {
            case LATEST -> findOrderByLatest(search);
            case PRICE_ASC -> findOrderByPriceAsc(search);
            case LIKES_DESC -> findOrderByLikesDesc(search);
        };
        List<Product> content = page.getContent().stream()
                .map(productMapper::toDomain)
                .toList();
        return new PageResult<>(
                content,
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

    private Page<ProductJpaEntity> findOrderByLatest(ProductCommand.Search s) {
        Pageable pageable = PageRequest.of(s.page(), s.size(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return s.brandId() == null
                ? productJpaRepository.findAllByDeletedAtIsNull(pageable)
                : productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(s.brandId(), pageable);
    }

    private Page<ProductJpaEntity> findOrderByPriceAsc(ProductCommand.Search s) {
        Pageable pageable = PageRequest.of(s.page(), s.size(),
                Sort.by(Sort.Order.asc("price"), Sort.Order.desc("id")));
        return s.brandId() == null
                ? productJpaRepository.findAllByDeletedAtIsNull(pageable)
                : productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(s.brandId(), pageable);
    }

    private Page<ProductJpaEntity> findOrderByLikesDesc(ProductCommand.Search s) {
        Pageable pageable = PageRequest.of(s.page(), s.size());
        return s.brandId() == null
                ? productJpaRepository.findAllOrderByLikesDesc(pageable)
                : productJpaRepository.findAllByBrandIdOrderByLikesDesc(s.brandId(), pageable);
    }
}
