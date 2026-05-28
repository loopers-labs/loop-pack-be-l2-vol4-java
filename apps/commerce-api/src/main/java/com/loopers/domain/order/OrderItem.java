package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

/** 주문 시점 상품 스냅샷. Order의 불변 구성요소(같은 애그리거트)라 BaseEntity 미상속. Product는 다른 애그리거트라 ID 참조. */
@Getter
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderModel order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    protected OrderItem() {}

    public OrderItem(Long productId, String productName, Long unitPrice, Integer quantity) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "제품 ID는 비어있을 수 없습니다.");
        }
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목의 상품명은 비어있을 수 없습니다.");
        }
        if (unitPrice == null || unitPrice < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "단가는 0원 이상이어야 합니다.");
        }
        if (quantity == null || quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1개 이상이어야 합니다.");
        }
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    /** 패키지-비공개 — {@link OrderModel#addItem(OrderItem)}만 호출. 외부에서 부모를 바꾸지 못하게 막는다. */
    void assignTo(OrderModel order) {
        this.order = order;
    }

    public Long subtotal() {
        return unitPrice * quantity;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = ZonedDateTime.now();
    }
}
