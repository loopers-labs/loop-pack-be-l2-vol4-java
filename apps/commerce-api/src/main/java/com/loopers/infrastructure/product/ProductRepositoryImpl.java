package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
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
    public List<ProductModel> findAll() {
        return productJpaRepository.findAll().stream()
                .map(ProductEntityMapper::toDomain)
                .toList();
    }

    @Override
    public void delete(Long id) {
        productJpaRepository.deleteById(id);
    }
}
