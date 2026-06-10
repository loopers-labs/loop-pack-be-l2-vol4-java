package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Entity
@Table(name = "stock")
public class StockModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected StockModel() {}

    public StockModel(Long productId, Integer quantity) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "productId는 비어있을 수 없습니다.");
        }
        if (quantity == null || quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
        this.productId = productId;
        this.quantity = quantity;
    }

    public void decrease(int amount) {
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        }
        if (this.quantity - amount < 0) {
            throw new CoreException(ErrorType.CONFLICT, "재고가 부족합니다.");
        }
        this.quantity -= amount;
    }

    public void increase(int amount) {
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "증가 수량은 1 이상이어야 합니다.");
        }
        this.quantity += amount;
    }

    public boolean isAvailable() {
        return this.quantity > 0;
    }

    @PrePersist
    void prePersist() {
        ZonedDateTime now = ZonedDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }
}
