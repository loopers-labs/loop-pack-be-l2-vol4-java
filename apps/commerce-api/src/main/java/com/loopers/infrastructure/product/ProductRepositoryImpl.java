package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
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

    /**
     * 순수 도메인 ↔ JPA 엔티티 경계.
     * - 신규(id == null): 매퍼로 엔티티를 만들어 INSERT.
     * - 기존(id != null): managed 엔티티를 로드해 가변 상태(이름/설명/이미지/가격/재고/좋아요 수)만 복사 → dirty checking으로 UPDATE.
     *   soft delete 상태(deletedAt)도 도메인 기준으로 delete()/restore() 동기화한다(둘 다 멱등).
     *   (BaseEntity의 id가 final이라 도메인을 그대로 새 엔티티로 만들면 INSERT로 오인되므로 이 경로가 필요하다.)
     */
    @Override
    public ProductModel save(ProductModel product) {
        if (product.getId() == null) {
            ProductEntity saved = productJpaRepository.save(ProductEntityMapper.toEntity(product));
            return ProductEntityMapper.toDomain(saved);
        }
        ProductEntity entity = productJpaRepository.findById(product.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + product.getId() + "] 상품을 찾을 수 없습니다."));
        entity.applyState(product.getName(), product.getDescription(), product.getImageUrl(),
                product.getPrice(), product.getStock(), product.getLikesCount());
        if (product.isActive()) {
            entity.restore();
        } else {
            entity.delete();
        }
        return ProductEntityMapper.toDomain(productJpaRepository.save(entity));
    }

    @Override
    public Optional<ProductModel> find(Long id) {
        return productJpaRepository.findById(id).map(ProductEntityMapper::toDomain);
    }

    @Override
    public List<ProductModel> findActiveByBrandId(Long brandId) {
        return productJpaRepository.findByBrandIdAndDeletedAtIsNull(brandId).stream()
                .map(ProductEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<ProductModel> findActivePage(Long brandId, ProductSortType sort, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, toSort(sort));
        List<ProductEntity> entities = (brandId == null)
                ? productJpaRepository.findByDeletedAtIsNull(pageable)
                : productJpaRepository.findByBrandIdAndDeletedAtIsNull(brandId, pageable);
        return entities.stream().map(ProductEntityMapper::toDomain).toList();
    }

    /** 정렬 + id DESC tiebreaker로 페이지 경계 안정성 보장 (01 §7.2). */
    private static Sort toSort(ProductSortType sort) {
        Sort byIdDesc = Sort.by(Sort.Direction.DESC, "id");
        ProductSortType effective = (sort == null) ? ProductSortType.LATEST : sort;
        return switch (effective) {
            case LATEST -> byIdDesc;
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price").and(byIdDesc);
            case PRICE_DESC -> Sort.by(Sort.Direction.DESC, "price").and(byIdDesc);
            case LIKES_DESC -> Sort.by(Sort.Direction.DESC, "likesCount").and(byIdDesc);
        };
    }
}
