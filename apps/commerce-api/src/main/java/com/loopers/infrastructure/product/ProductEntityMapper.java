package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;

/**
 * ProductModel(순수 도메인) ↔ ProductEntity(JPA) 변환기. 도메인과 영속 경계 사이의 번역만 담당한다.
 * 재고는 독립 Aggregate(stock 테이블)라 여기서 다루지 않으며, soft delete 상태(deletedAt)는 양방향 동기화한다.
 */
public final class ProductEntityMapper {

    private ProductEntityMapper() {}

    public static ProductEntity toEntity(ProductModel product) {
        return new ProductEntity(
                product.getBrandId(),
                product.getName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getPrice(),
                product.getLikesCount()
        );
    }

    public static ProductModel toDomain(ProductEntity entity) {
        return ProductModel.reconstitute(
                entity.getId(),
                entity.getBrandId(),
                entity.getName(),
                entity.getDescription(),
                entity.getImageUrl(),
                entity.getPrice(),
                entity.getLikesCount(),
                entity.getDeletedAt()
        );
    }
}
