package com.loopers.domain.stock;

import com.loopers.domain.common.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.ZonedDateTime;

@Entity
@Table(name = "stocks")
public class StockModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", unique = true, nullable = false)
    private Long productId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "quantity", nullable = false))
    private Quantity quantity;

    @Version
    private Long version;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    protected StockModel() {}

    public StockModel(Long productId, int quantity) {
        this.productId = productId;
        this.quantity = Quantity.of(quantity);   // 음수 검증은 VO 내부에서
    }

    public static StockModel of(Long productId, int quantity) {
        return new StockModel(productId, quantity);
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }

    public void deduct(int qty) {
        Quantity amount = Quantity.of(qty);
        if (!amount.isPositive()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        }
        if (!this.quantity.isGreaterThanOrEqual(amount)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
        this.quantity = this.quantity.minus(amount);
    }

    public void restore(int qty) {
        Quantity amount = Quantity.of(qty);
        if (!amount.isPositive()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "복구 수량은 1 이상이어야 합니다.");
        }
        this.quantity = this.quantity.plus(amount);
    }

    /**
     * 재고 수량을 절대값으로 설정한다. 어드민이 상품 수정 시 재고를 직접 조정하는 용도.
     */
    public void changeQuantity(int newQuantity) {
        this.quantity = Quantity.of(newQuantity);  // 음수 검증 VO 내부
    }

    public boolean hasEnough(int qty) {
        return this.quantity.isGreaterThanOrEqual(Quantity.of(qty));
    }

    public boolean isAvailable() {
        return this.quantity.isPositive();
    }

    public Integer getDisplayQuantity() {
        int value = this.quantity.getValue();
        if (value <= 10) {
            return value;
        }
        return null;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    /** 수량 (DTO/응답용 — int). 도메인 내부에서는 {@link Quantity} 로 캡슐화되어 있다. */
    public int getQuantity() {
        return quantity.getValue();
    }

    public Long getVersion() {
        return version;
    }
}
