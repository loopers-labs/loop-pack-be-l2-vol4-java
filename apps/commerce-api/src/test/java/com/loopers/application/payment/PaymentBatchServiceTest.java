package com.loopers.application.payment;

import com.loopers.application.order.OrderService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.pg.PgClient;
import com.loopers.infrastructure.pg.PgResponse;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
class PaymentBatchServiceTest {

    @Autowired
    private PaymentBatchService paymentBatchService;

    @MockitoBean
    private PgClient pgClient;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("PENDING 결제 복구 배치를 실행할 때,")
    @Nested
    class Recover {

        @DisplayName("PG에 결제 기록이 없으면, 결제가 FAILED 처리되고 주문이 CANCELLED된다.")
        @Test
        void recover_failsPayment_whenPgHasNoRecord() {
            // arrange
            OrderModel order = orderJpaRepository.save(new OrderModel(1L, null, 10000L, 0L));
            paymentJpaRepository.save(new PaymentModel(order.getId(), CardType.SAMSUNG, "1234-5678-9012-3456", 10000L));
            when(pgClient.getTransactionsByOrderId(any(), any()))
                .thenThrow(new feign.FeignException.NotFound(
                    "Not Found",
                    feign.Request.create(feign.Request.HttpMethod.GET, "http://localhost/", java.util.Collections.emptyMap(), null, java.nio.charset.StandardCharsets.UTF_8, null),
                    null, null));

            // act
            paymentBatchService.recover(ZonedDateTime.now().plusHours(1));

            // assert
            PaymentModel payment = paymentJpaRepository.findByOrderId(order.getId()).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);

            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @DisplayName("PG 상태가 PENDING이면, 결제 상태를 변경하지 않는다.")
        @Test
        void recover_keepsPending_whenPgIsPending() {
            // arrange
            OrderModel order = orderJpaRepository.save(new OrderModel(1L, null, 10000L, 0L));
            paymentJpaRepository.save(new PaymentModel(order.getId(), CardType.SAMSUNG, "1234-5678-9012-3456", 10000L));
            when(pgClient.getTransactionsByOrderId(any(), any()))
                .thenReturn(new PgResponse.OrderResponse(
                    String.format("%012d", order.getId()),
                    List.of(new PgResponse.TransactionResponse("20250626:TR:abc123", "PENDING", null))
                ));

            // act
            paymentBatchService.recover(ZonedDateTime.now().plusHours(1));

            // assert
            PaymentModel payment = paymentJpaRepository.findByOrderId(order.getId()).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @DisplayName("PG 상태가 SUCCESS이고 주문이 PENDING이면, 결제가 SUCCESS로 변경되고 주문이 CONFIRMED된다.")
        @Test
        void recover_successesPayment_whenPgIsSuccess() {
            // arrange
            OrderModel order = orderJpaRepository.save(new OrderModel(1L, null, 10000L, 0L));
            paymentJpaRepository.save(new PaymentModel(order.getId(), CardType.SAMSUNG, "1234-5678-9012-3456", 10000L));
            when(pgClient.getTransactionsByOrderId(any(), any()))
                .thenReturn(new PgResponse.OrderResponse(
                    String.format("%012d", order.getId()),
                    List.of(new PgResponse.TransactionResponse("20250626:TR:abc123", "SUCCESS", null))
                ));

            // act
            paymentBatchService.recover(ZonedDateTime.now().plusHours(1));

            // assert
            PaymentModel payment = paymentJpaRepository.findByOrderId(order.getId()).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(payment.getTransactionKey()).isEqualTo("20250626:TR:abc123");

            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @DisplayName("PG 상태가 FAILED이면, 결제가 FAILED 처리되고 주문이 CANCELLED된다.")
        @Test
        void recover_failsPayment_whenPgIsFailed() {
            // arrange
            OrderModel order = orderJpaRepository.save(new OrderModel(1L, null, 10000L, 0L));
            paymentJpaRepository.save(new PaymentModel(order.getId(), CardType.SAMSUNG, "1234-5678-9012-3456", 10000L));
            when(pgClient.getTransactionsByOrderId(any(), any()))
                .thenReturn(new PgResponse.OrderResponse(
                    String.format("%012d", order.getId()),
                    List.of(new PgResponse.TransactionResponse("20250626:TR:abc123", "FAILED", "한도 초과"))
                ));

            // act
            paymentBatchService.recover(ZonedDateTime.now().plusHours(1));

            // assert
            PaymentModel payment = paymentJpaRepository.findByOrderId(order.getId()).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);

            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @DisplayName("PG 상태가 SUCCESS이지만 주문이 이미 CANCELLED이면, 결제가 CONFLICT로 마킹된다.")
        @Test
        void recover_skips_whenPgIsSuccessButOrderCancelled() {
            // arrange
            OrderModel order = orderJpaRepository.save(new OrderModel(1L, null, 10000L, 0L));
            orderService.cancelBySystem(order.getId());
            paymentJpaRepository.save(new PaymentModel(order.getId(), CardType.SAMSUNG, "1234-5678-9012-3456", 10000L));
            when(pgClient.getTransactionsByOrderId(any(), any()))
                .thenReturn(new PgResponse.OrderResponse(
                    String.format("%012d", order.getId()),
                    List.of(new PgResponse.TransactionResponse("20250626:TR:abc123", "SUCCESS", null))
                ));

            // act
            paymentBatchService.recover(ZonedDateTime.now().plusHours(1));

            // assert - PG SUCCESS이나 주문 취소 → CONFLICT 마킹
            PaymentModel payment = paymentJpaRepository.findByOrderId(order.getId()).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CONFLICT);

            OrderModel updatedOrder = orderJpaRepository.findById(order.getId()).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }
    }
}
