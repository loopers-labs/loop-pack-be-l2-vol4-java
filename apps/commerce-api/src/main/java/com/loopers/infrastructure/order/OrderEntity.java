package com.loopers.infrastructure.order;

import com.loopers.domain.AuditEntity;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.order.PaymentMethod;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * orders 테이블 JPA 매핑 전용 엔티티. 순수 도메인(OrderModel)과 분리되어 영속 관심사만 담는다.
 * 도메인 ↔ 엔티티 변환은 OrderEntityMapper가 담당.
 * <p>
 * 식별자는 DB auto-increment가 아니라 <b>앱이 생성한 TSID</b>를 그대로 PK로 받는다(OrderModel 생성자에서 발급).
 * 그래서 BaseEntity(IDENTITY) 대신 id 없는 {@link AuditEntity}를 상속하고 @Id를 직접 선언한다.
 * id가 신규에도 미리 채워져 있으므로 Spring Data가 INSERT(persist)/UPDATE(merge)를 오판하지 않도록
 * {@link Persistable#isNew()}로 "아직 영속 전(createdAt == null)"임을 알려준다.
 */
@Entity
@Table(name = "orders")
public class OrderEntity extends AuditEntity implements Persistable<Long> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount;

    @Column(name = "final_amount", nullable = false)
    private Long finalAmount;

    @Column(name = "user_coupon_id")
    private Long userCouponId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "paid_at")
    private ZonedDateTime paidAt;

    // 아그리거트 루트의 composition — 주문 로드 시 항목은 항상 함께 필요하므로 EAGER (단건 조회 위주)
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderItemEntity> items = new ArrayList<>();

    protected OrderEntity() {}

    public OrderEntity(Long id, Long userId, OrderStatus status, Long totalAmount, Long discountAmount, Long finalAmount,
                       Long userCouponId, PaymentMethod paymentMethod, String failureReason,
                       ZonedDateTime paidAt, List<OrderItemEntity> items) {
        this.id = id;
        this.userId = userId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.userCouponId = userCouponId;
        this.paymentMethod = paymentMethod;
        this.failureReason = failureReason;
        this.paidAt = paidAt;
        this.items = new ArrayList<>(items);
    }

    /**
     * 결제 결과 반영 시 변경되는 가변 상태만 갱신한다.
     * managed 엔티티에 적용 → JPA dirty checking이 UPDATE로 반영 (항목은 변하지 않으므로 건드리지 않음).
     */
    public void applyState(OrderStatus status, Long totalAmount, String failureReason, ZonedDateTime paidAt) {
        this.status = status;
        this.totalAmount = totalAmount;
        this.failureReason = failureReason;
        this.paidAt = paidAt;
    }

    @Override
    public Long getId() {
        return id;
    }

    /** 아직 영속 전이면 createdAt이 비어 있다(@PrePersist에서 채워짐) → INSERT(persist)로 처리하게 한다. */
    @Override
    public boolean isNew() {
        return getCreatedAt() == null;
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Long getTotalAmount() {
        return totalAmount;
    }

    public Long getDiscountAmount() {
        return discountAmount;
    }

    public Long getFinalAmount() {
        return finalAmount;
    }

    public Long getUserCouponId() {
        return userCouponId;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public ZonedDateTime getPaidAt() {
        return paidAt;
    }

    public List<OrderItemEntity> getItems() {
        return Collections.unmodifiableList(items);
    }
}
