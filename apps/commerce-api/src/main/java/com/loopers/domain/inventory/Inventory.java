package com.loopers.domain.inventory;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 재고 — 상품(다른 애그리거트)의 가용 수량을 독립적으로 관리하는 애그리거트.
 * {@code products} 행과 분리해, 주문의 비관락(FOR UPDATE)이 좋아요 카운터 등
 * 무관한 쓰기와 같은 행 락을 두고 싸우지 않게 한다(false sharing 제거).
 * 상품은 {@code productId} 로 ID 참조만 하며 객체 그래프는 만들지 않는다.
 * 수량(quantity)은 단일 값이라 별도 VO 로 감싸지 않고 이 애그리거트가 직접 보유한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 변경된 컬럼만 UPDATE — 소프트 삭제(deleted_at) 가 동시 주문의 quantity 차감을 stale 값으로 덮어쓰지 않게 한다.
@DynamicUpdate
@Table(
        name = "inventories",
        uniqueConstraints = @UniqueConstraint(name = "uk_inventories_product", columnNames = "product_id")
)
public class Inventory extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    private Inventory(Long productId, int quantity) {
        validateProductId(productId);
        validateQuantity(quantity);
        this.productId = productId;
        this.quantity = quantity;
    }

    public static Inventory create(Long productId, int quantity) {
        return new Inventory(productId, quantity);
    }

    public void decrease(int qty) {
        if (qty <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 0보다 커야 합니다.");
        }
        if (quantity < qty) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 재고가 부족합니다.");
        }
        this.quantity -= qty;
    }

    public void adjust(int newQuantity) {
        validateQuantity(newQuantity);
        this.quantity = newQuantity;
    }

    public boolean isSoldOut() {
        return quantity == 0;
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }

    private void validateProductId(Long productId) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 비어있을 수 없습니다.");
        }
    }

    private void validateQuantity(int quantity) {
        if (quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
    }
}
