package com.loopers.order.application;

import com.loopers.order.domain.OrderStatus;
import com.loopers.order.infrastructure.OrderJpaRepository;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.infrastructure.ProductJpaRepository;
import com.loopers.stock.domain.StockModel;
import com.loopers.stock.infrastructure.StockJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderFacadeIntegrationTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private StockJpaRepository stockJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private ProductModel savedProduct(int totalStock) {
        ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, null));
        stockJpaRepository.save(new StockModel(product.getId(), totalStock));
        return product;
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("정상 요청이면, PENDING_PAYMENT 상태로 저장되고 재고 변동이 없다.")
        @Test
        void returnsOrderInfo_withPendingPaymentStatus_andNoStockChange_whenRequestIsValid() {
            // arrange
            ProductModel product = savedProduct(100);

            // act
            OrderInfo result = orderFacade.createOrder(1L, List.of(new OrderItemCommand(product.getId(), 2)));

            // assert
            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.status()).isEqualTo(OrderStatus.PENDING_PAYMENT.name()),
                () -> assertThat(result.items()).hasSize(1)
            );

            StockModel stock = stockJpaRepository.findByProductId(product.getId()).orElseThrow();
            assertThat(stock.availableStock()).isEqualTo(100);
        }

        @DisplayName("존재하지 않는 productId가 포함되면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.createOrder(1L, List.of(new OrderItemCommand(999L, 1)))
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("결제를 시작(startPayment)할 때,")
    @Nested
    class StartPayment {

        @DisplayName("정상 요청이면, 재고가 선점된다.")
        @Test
        void reservesStock_whenRequestIsValid() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderInfo order = orderFacade.createOrder(1L, List.of(new OrderItemCommand(product.getId(), 5)));

            // act
            orderFacade.startPayment(1L, order.id());

            // assert
            StockModel stock = stockJpaRepository.findByProductId(product.getId()).orElseThrow();
            assertAll(
                () -> assertThat(stock.getReservedStock()).isEqualTo(5),
                () -> assertThat(stock.availableStock()).isEqualTo(95)
            );
        }

        @DisplayName("재고가 부족하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsInsufficient() {
            // arrange
            ProductModel product = savedProduct(1);
            OrderInfo order = orderFacade.createOrder(1L, List.of(new OrderItemCommand(product.getId(), 5)));

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.startPayment(1L, order.id())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 orderId이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.startPayment(1L, 999L)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("다른 유저의 주문이면, FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenOrderBelongsToAnotherUser() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderInfo order = orderFacade.createOrder(1L, List.of(new OrderItemCommand(product.getId(), 1)));

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.startPayment(2L, order.id())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }

        @DisplayName("주문 품목 중 재고 레코드가 없는 상품이 있으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenStockNotExists() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, null));
            OrderInfo order = orderFacade.createOrder(1L, List.of(new OrderItemCommand(product.getId(), 1)));

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.startPayment(1L, order.id())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("결제를 확정(confirmPayment)할 때,")
    @Nested
    class ConfirmPayment {

        @DisplayName("정상 요청이면, 재고가 확정 차감되고 주문 상태가 CONFIRMED로 변경된다.")
        @Test
        void confirmsStockAndChangesStatusToConfirmed_whenRequestIsValid() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderInfo order = orderFacade.createOrder(1L, List.of(new OrderItemCommand(product.getId(), 3)));
            orderFacade.startPayment(1L, order.id());

            // act
            OrderInfo result = orderFacade.confirmPayment(1L, order.id());

            // assert
            assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED.name());

            StockModel stock = stockJpaRepository.findByProductId(product.getId()).orElseThrow();
            assertAll(
                () -> assertThat(stock.getTotalStock()).isEqualTo(97),
                () -> assertThat(stock.getReservedStock()).isEqualTo(0)
            );
        }

        @DisplayName("존재하지 않는 orderId이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.confirmPayment(1L, 999L)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("다른 유저의 주문이면, FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenOrderBelongsToAnotherUser() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderInfo order = orderFacade.createOrder(1L, List.of(new OrderItemCommand(product.getId(), 1)));
            orderFacade.startPayment(1L, order.id());

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.confirmPayment(2L, order.id())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }
    }

    @DisplayName("주문을 단건 조회할 때,")
    @Nested
    class GetOrder {

        @DisplayName("존재하는 주문 ID이면, OrderInfo를 반환한다.")
        @Test
        void returnsOrderInfo_whenOrderExists() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderInfo created = orderFacade.createOrder(1L, List.of(new OrderItemCommand(product.getId(), 1)));

            // act
            OrderInfo result = orderFacade.getOrder(1L, created.id());

            // assert
            assertAll(
                () -> assertThat(result.id()).isEqualTo(created.id()),
                () -> assertThat(result.userId()).isEqualTo(1L),
                () -> assertThat(result.items()).hasSize(1)
            );
        }

        @DisplayName("존재하지 않는 주문 ID이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderNotExists() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.getOrder(1L, 999L)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("다른 유저의 주문 ID이면, FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenOrderBelongsToAnotherUser() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderInfo created = orderFacade.createOrder(1L, List.of(new OrderItemCommand(product.getId(), 1)));

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.getOrder(2L, created.id())
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }
    }
}
