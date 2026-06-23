package com.loopers.application.payment

import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentEvent
import com.loopers.domain.payment.PaymentEventPublisher
import com.loopers.domain.payment.PaymentRelay
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.TransactionKeyGenerator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PaymentApplicationServiceTest {

    @DisplayName("same user and order payment request returns existing transaction without creating a duplicate")
    @Test
    fun returnsExistingTransaction_whenSameUserAndOrderRequestedAgain() {
        // arrange
        val repository = FakePaymentRepository()
        val publisher = FakePaymentEventPublisher()
        val service = PaymentApplicationService(
            paymentRepository = repository,
            paymentEventPublisher = publisher,
            paymentRelay = FakePaymentRelay(),
            transactionKeyGenerator = TransactionKeyGenerator(),
        )
        val command = PaymentCommand.CreateTransaction(
            userId = "user1",
            orderId = "000001",
            cardType = CardType.SAMSUNG,
            cardNo = "1234-5678-9814-1451",
            amount = 1000L,
            callbackUrl = "http://localhost:8080/api/v1/payments/callback",
        )

        // act
        val first = service.createTransaction(command)
        val second = service.createTransaction(command)

        // assert
        assertThat(second.transactionKey).isEqualTo(first.transactionKey)
        assertThat(repository.payments).hasSize(1)
        assertThat(publisher.createdEvents).hasSize(1)
    }

    private class FakePaymentRepository : PaymentRepository {
        val payments = linkedMapOf<String, Payment>()

        override fun save(payment: Payment): Payment {
            payments[payment.transactionKey] = payment
            return payment
        }

        override fun findByTransactionKey(transactionKey: String): Payment? {
            return payments[transactionKey]
        }

        override fun findByTransactionKey(userId: String, transactionKey: String): Payment? {
            return payments[transactionKey]?.takeIf { it.userId == userId }
        }

        override fun findByOrderId(userId: String, orderId: String): List<Payment> {
            return payments.values
                .filter { it.userId == userId && it.orderId == orderId }
        }
    }

    private class FakePaymentEventPublisher : PaymentEventPublisher {
        val createdEvents = mutableListOf<PaymentEvent.PaymentCreated>()

        override fun publish(event: PaymentEvent.PaymentCreated) {
            createdEvents.add(event)
        }

        override fun publish(event: PaymentEvent.PaymentHandled) {}
    }

    private class FakePaymentRelay : PaymentRelay {
        override fun notify(callbackUrl: String, transactionInfo: TransactionInfo) {}
    }
}
