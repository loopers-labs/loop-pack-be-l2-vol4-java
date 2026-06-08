package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    public List<ProductModel> findAllByIds(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }

    @Override
    public List<ProductModel> findAll(Long brandId, String sort, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        String sortKey = sort == null ? "latest" : sort;
        return switch (sortKey) {
            case "price_asc" -> productJpaRepository.findAllByPriceAsc(brandId, pageable);
            case "likes_desc" -> productJpaRepository.findAllByLikesDesc(brandId, pageable);
            default -> productJpaRepository.findAllByLatest(brandId, pageable);
        };
    }

    @Override
    public List<ProductModel> findAllByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandId(brandId);
    }
}
