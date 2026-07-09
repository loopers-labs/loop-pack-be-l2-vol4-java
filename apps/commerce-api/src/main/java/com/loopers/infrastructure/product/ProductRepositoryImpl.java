package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {
    private final ProductJpaRepository productJpaRepository;
    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    /**
     * product 저장의 단일 관문. 순수 도메인 ↔ JPA 엔티티 경계이자, product_metrics(read model)의
     * <b>차원 컬럼</b> 동기화 지점이다.
     * <ul>
     *   <li>신규(id == null): 매퍼로 엔티티를 만들어 INSERT + product_metrics 행 생성(측정값 0).</li>
     *   <li>기존(id != null): managed 엔티티를 로드해 가변 상태(이름/설명/이미지/가격)만 복사 → dirty checking UPDATE.
     *       soft delete(deletedAt)도 도메인 기준으로 delete()/restore() 동기화(멱등) + product_metrics 차원 UPDATE.</li>
     * </ul>
     * 생성/수정/삭제/복원 + Brand→Product cascade 삭제가 모두 이 경로를 타므로 read model 동기화를 한 곳에서 커버한다.
     * 측정값 컬럼(like/sales/view)은 건드리지 않아 streamer 누적분이 보존된다.
     *
     * <p>product + product_metrics 두 write 를 원자적으로 묶고, 차원 쓰기(@Modifying)가 트랜잭션을 요구하므로
     * 이 메서드 자체를 트랜잭션 경계로 둔다(서비스가 이미 @Transactional 이면 그 트랜잭션에 합류).
     */
    @Transactional
    @Override
    public ProductModel save(ProductModel product) {
        if (product.getId() == null) {
            ProductEntity saved = productJpaRepository.save(ProductEntityMapper.toEntity(product));
            productMetricsJpaRepository.insertDimensions(saved.getId(), saved.getBrandId(), saved.getPrice());
            return ProductEntityMapper.toDomain(saved);
        }
        ProductEntity entity = productJpaRepository.findById(product.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + product.getId() + "] 상품을 찾을 수 없습니다."));
        entity.applyState(product.getName(), product.getDescription(), product.getImageUrl(),
                product.getPrice());
        if (product.isActive()) {
            entity.restore();
        } else {
            entity.delete();
        }
        ProductEntity persisted = productJpaRepository.save(entity);
        productMetricsJpaRepository.updateDimensions(
                persisted.getId(), persisted.getPrice(), persisted.getDeletedAt(), ZonedDateTime.now());
        return ProductEntityMapper.toDomain(persisted);
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
    public List<ProductModel> findActiveByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return productJpaRepository.findByIdInAndDeletedAtIsNull(ids).stream()
                .map(ProductEntityMapper::toDomain)
                .toList();
    }
}
