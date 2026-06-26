package com.loopers.infrastructure.inventory;

import com.loopers.infrastructure.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "inventory",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ref_product_id"}, name="unique_product_id"),
        indexes = {
            @Index(name = "idx_inventory_product_id_deleted_at", columnList = "ref_product_id, deleted_at"),
        }

)
@Getter
public class InventoryJpaEntity extends BaseJpaEntity {

    @Column(name = "ref_product_id", nullable = false)
    private String productId;

    @Column(nullable = false)
    private Integer quantity;

    protected InventoryJpaEntity() {}

    @Override
    protected String idCode() {
        return "INV";
    }

    InventoryJpaEntity(String id, String productId, Integer quantity, ZonedDateTime deletedAt) {
        super(id, deletedAt);
        this.productId = productId;
        this.quantity = quantity;
    }
}
