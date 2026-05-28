package com.loopers.domain.stock;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "stocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockModel extends BaseEntity {

    @Column(name = "product_id", nullable = false, unique = true)
    private UUID productId;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    public StockModel(UUID productId, int totalQuantity) {
        if (totalQuantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "초기 재고는 0 이상이어야 합니다.");
        }
        this.productId = productId;
        this.totalQuantity = totalQuantity;
        this.reservedQuantity = 0;
    }

    public int getAvailableQuantity() {
        return totalQuantity - reservedQuantity;
    }

    /**
     * 재고 예약 — 도메인 불변식 검증 (단위 테스트용).
     * 실제 동시성 환경에서는 StockRepository.reserve() 조건부 UPDATE로 원자 처리.
     */
    public void reserve(int qty) {
        if (qty <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "예약 수량은 1 이상이어야 합니다.");
        }
        if (getAvailableQuantity() < qty) {
            throw new CoreException(ErrorType.CONFLICT, "재고가 부족합니다.");
        }
        this.reservedQuantity += qty;
    }

    /** 결제 확정 — reserved 차감 + total 차감 */
    public void confirm(int qty) {
        this.totalQuantity -= qty;
        this.reservedQuantity -= qty;
    }

    /** 결제 실패/만료 — reserved만 해제, total 유지 */
    public void release(int qty) {
        this.reservedQuantity -= qty;
    }

    /** 주문 취소(confirm 이후) — total 복구 */
    public void restore(int qty) {
        this.totalQuantity += qty;
    }

    /** 어드민 재고 수정 — reserved 미만이면 불변식 위반으로 거부 */
    public void updateTotal(int newTotal) {
        if (newTotal < this.reservedQuantity) {
            throw new CoreException(ErrorType.CONFLICT, "수정할 재고가 예약 중인 수량보다 작을 수 없습니다.");
        }
        this.totalQuantity = newTotal;
    }
}
