package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * Stock Aggregate 루트 — 순수 도메인 객체. 특정 상품(productId)의 재고 수량과
 * "0 이상" 불변식·차감/복원/가용성 행동을 캡슐화한다. 영속 기술(JPA)에는 의존하지 않으며,
 * JPA 매핑은 infrastructure.stock.StockEntity가, 변환은 StockEntityMapper가 담당한다.
 *
 * Product와 1:1이지만 독립 Aggregate로서 productId(ID 참조)로만 연결한다(객체 참조 금지 — 03 §1).
 * 동시성은 영속 계층(@Version 낙관 / SELECT ... FOR UPDATE 비관)에서 보장한다.
 */
public class StockModel {

    private final Long id;        // 영속 전에는 null, 저장 후 매퍼가 채운 값으로 복원된다.
    private final Long productId;
    private int quantity;

    public StockModel(Long productId, int quantity) {
        this.id = null;
        this.productId = validateProductId(productId);
        this.quantity = validateQuantity(quantity);
    }

    private StockModel(Long id, Long productId, int quantity) {
        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
    }

    /** 영속 데이터로부터 도메인 객체를 복원한다 (infrastructure 매퍼 전용). */
    public static StockModel reconstitute(Long id, Long productId, int quantity) {
        return new StockModel(id, productId, quantity);
    }

    // --- 검증 ---

    private static Long validateProductId(Long productId) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "productId는 null일 수 없습니다.");
        }
        return productId;
    }

    private static int validateQuantity(int quantity) {
        if (quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
        return quantity;
    }

    // --- 도메인 메서드 ---

    /** 차감. 수량 1 이상이어야 하고, 부족하면 CONFLICT (04 §4.3). */
    public void deduct(int amount) {
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        }
        if (this.quantity < amount) {
            throw new CoreException(ErrorType.CONFLICT, "재고가 부족합니다. (현재 재고: " + this.quantity + ")");
        }
        this.quantity -= amount;
    }

    /** 복원 (결제 실패 시 원복 — 01 §7.6). */
    public void restore(int amount) {
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "복원 수량은 1 이상이어야 합니다.");
        }
        this.quantity += amount;
    }

    /** 재고를 절대값으로 교체 (Admin 상품 수정 — 0 이상). */
    public void changeQuantity(int newQuantity) {
        this.quantity = validateQuantity(newQuantity);
    }

    public boolean isAvailable(int amount) {
        return this.quantity >= amount;
    }

    // --- Getter ---

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }
}
