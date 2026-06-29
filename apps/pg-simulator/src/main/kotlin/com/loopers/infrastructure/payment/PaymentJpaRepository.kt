package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentJpaRepository : JpaRepository<Payment, String> {
    fun findByIdempotencyKey(idempotencyKey: String): Payment?
    fun findByUserIdAndTransactionKey(userId: String, transactionKey: String): Payment?
    fun findByUserIdAndOrderId(userId: String, orderId: String): List<Payment>
}
