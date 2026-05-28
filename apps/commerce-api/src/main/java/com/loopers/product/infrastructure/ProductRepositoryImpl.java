package com.loopers.product.infrastructure;

import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<Product> findActiveById(Long id) {
        return productJpaRepository.findByIdAndStatusAndDeletedAtIsNull(id, ProductStatus.ON_SALE);
    }

    @Override
    public List<Product> findAllOnSaleOrderByLatest() {
        return productJpaRepository.findAllByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(ProductStatus.ON_SALE);
    }

    @Override
    public List<Product> findAllOnSaleOrderByPriceAsc() {
        return productJpaRepository.findAllByStatusAndDeletedAtIsNullOrderByPriceAsc(ProductStatus.ON_SALE);
    }

    @Override
    public List<Product> findAllOnSaleOrderByLikeCountDesc() {
        return productJpaRepository.findOnSaleOrderByLikeCountDesc();
    }

    @Override
    public List<Product> findAllOrderByLatest() {
        return productJpaRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc();
    }

    @Override
    public List<Product> findAllByIdIn(List<Long> ids) {
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }

    @Override
    public int softDeleteByBrandId(Long brandId) {
        return productJpaRepository.softDeleteByBrandId(brandId);
    }
}
