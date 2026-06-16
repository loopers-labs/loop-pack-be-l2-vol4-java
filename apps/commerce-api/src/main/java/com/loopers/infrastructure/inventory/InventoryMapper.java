package com.loopers.infrastructure.inventory;

import com.loopers.domain.inventory.InventoryEntity;

public class InventoryMapper {

    public static InventoryJpaEntity toJpaEntity(InventoryEntity entity) {
        return new InventoryJpaEntity(
                entity.getId(),
                entity.getProductId(),
                entity.getQuantity(),
                entity.getDeletedAt()
        );
    }

    public static InventoryEntity toDomain(InventoryJpaEntity entity) {
        return InventoryEntity.of(
                entity.getId(),
                entity.getProductId(),
                entity.getQuantity(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt()
        );
    }
}
