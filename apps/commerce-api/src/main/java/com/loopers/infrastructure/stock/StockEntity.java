package com.loopers.infrastructure.stock;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * stock 테이블 JPA 매핑 전용 엔티티. 순수 도메인(StockModel)과 분리되어 영속 관심사만 담는다.
 * product_id는 상품과 1:1(UNIQUE)이며, version은 낙관적 락(@Version)용 — 동시 차감에서
 * "정확히 한 번" 반영을 보장한다. 비관적 락은 조회 쿼리(@Lock FOR UPDATE)로 제공한다.
 * (재고는 soft delete 대상이 아니며, BaseEntity의 deletedAt 컬럼은 사용하지 않는다.)
 * 도메인 ↔ 엔티티 변환은 StockEntityMapper가 담당.
 */
@Entity
@Table(name = "stock")
public class StockEntity extends BaseEntity {

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected StockEntity() {}

    public StockEntity(Long productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    /** 변경 가능한 상태는 quantity뿐 (차감/복원). productId는 불변. */
    public void applyState(Integer quantity) {
        this.quantity = quantity;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getQuantity() {
        return quantity;
    }
}
