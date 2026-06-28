package com.loopers.domain.payment;

import com.loopers.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payments")
public class PaymentModel extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    public PaymentModel(Long orderId, PaymentMethod method, BigDecimal amount, String transactionId, LocalDateTime approvedAt) {
        this.orderId = orderId;
        this.method = method;
        this.amount = amount;
        this.status = PaymentStatus.APPROVED;
        this.transactionId = transactionId;
        this.approvedAt = approvedAt;
    }

    public PaymentModel(Long orderId, PaymentMethod method, BigDecimal amount) {
        this.orderId = orderId;
        this.method = method;
        this.amount = amount;
        this.status = PaymentStatus.READY;
    }

    public void approve(String transactionId, LocalDateTime approvedAt) {
        this.status = PaymentStatus.APPROVED;
        this.transactionId = transactionId;
        this.approvedAt = approvedAt;
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
    }
}
