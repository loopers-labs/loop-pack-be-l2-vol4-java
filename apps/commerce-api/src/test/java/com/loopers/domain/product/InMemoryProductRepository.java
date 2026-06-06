package com.loopers.domain.product;

import com.loopers.domain.product.enums.ProductSortType;
import com.loopers.domain.product.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class InMemoryProductRepository implements ProductRepository {

    private final List<ProductModel> store = new ArrayList<>();

    @Override
    public ProductModel save(ProductModel product) {
        store.add(product);
        return product;
    }

    @Override
    public Optional<ProductModel> find(Long id) {
        return store.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst();
    }

    @Override
    public boolean existsByBrandIdAndName(Long brandId, String name) {
        return store.stream()
                .anyMatch(p -> p.getBrandId().equals(brandId) && p.getName().equals(name));
    }

    @Override
    public List<ProductModel> findAllByBrandId(Long brandId) {
        return store.stream()
                .filter(p -> p.getBrandId().equals(brandId))
                .toList();
    }

    @Override
    public void suspendAllByBrandId(Long brandId) {
        store.stream()
                .filter(p -> p.getBrandId().equals(brandId))
                .forEach(ProductModel::delete);
    }

    @Override
    public Page<ProductModel> findAll(Long brandId, ProductSortType sort, Pageable pageable) {
        List<ProductModel> filtered = store.stream()
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE && p.getDeletedAt() == null)
                .filter(p -> brandId == null || p.getBrandId().equals(brandId))
                .toList();
        return new PageImpl<>(filtered, pageable, filtered.size());
    }

    @Override
    public Page<ProductModel> findAllForAdmin(Long brandId, Pageable pageable) {
        List<ProductModel> filtered = store.stream()
                .filter(p -> p.getDeletedAt() == null)
                .filter(p -> brandId == null || p.getBrandId().equals(brandId))
                .toList();
        return new PageImpl<>(filtered, pageable, filtered.size());
    }

    @Override
    public List<ProductModel> findAllByIds(List<Long> ids) {
        return store.stream()
                .filter(p -> ids.contains(p.getId()))
                .toList();
    }
}
