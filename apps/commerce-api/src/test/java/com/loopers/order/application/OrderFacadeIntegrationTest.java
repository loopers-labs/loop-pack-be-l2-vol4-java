package com.loopers.order.application;

import com.loopers.order.infrastructure.OrderJpaRepository;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.infrastructure.ProductJpaRepository;
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
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("정상 요청이면, DB에 저장되고 OrderInfo를 반환하며 재고가 감소한다.")
        @Test
        void returnsOrderInfo_andDecreasesStock_whenRequestIsValid() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));

            // act
            OrderInfo result = orderFacade.createOrder(1L, List.of(new OrderItemCommand(product.getId(), 2)));

            // assert
            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.userId()).isEqualTo(1L),
                () -> assertThat(result.status()).isEqualTo("ORDERED"),
                () -> assertThat(result.items()).hasSize(1),
                () -> assertThat(result.items().get(0).productName()).isEqualTo("에어맥스"),
                () -> assertThat(result.items().get(0).quantity()).isEqualTo(2)
            );

            // 재고 감소 확인
            ProductModel updated = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.getStock()).isEqualTo(98);
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

        @DisplayName("재고가 부족한 상품이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsInsufficient() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 1, null));

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.createOrder(1L, List.of(new OrderItemCommand(product.getId(), 2)))
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문을 단건 조회할 때,")
    @Nested
    class GetOrder {

        @DisplayName("존재하는 주문 ID이면, OrderInfo를 반환한다.")
        @Test
        void returnsOrderInfo_whenOrderExists() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));
            OrderInfo created = orderFacade.createOrder(1L, List.of(new OrderItemCommand(product.getId(), 1)));

            // act
            OrderInfo result = orderFacade.getOrder(created.id());

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
                orderFacade.getOrder(999L)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
