package com.loopers.domain.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.ProductStockRepository;
import com.loopers.domain.product.ProductStockService;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.domain.order.enums.OrderStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderServiceIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductStockService productStockService;
    @Autowired private ProductStockRepository productStockRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private ProductStockModel stock;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandRepository.save(new BrandModel("테스트브랜드"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), new ProductName("테스트상품")));
        stock = productStockRepository.save(new ProductStockModel(product, new Price(10000L), 10));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private static final Long TEST_USER_ID = 1L;

    private OrderModel placeOrder(int quantity) {
        ProductStockModel decreased = productStockService.decrease(stock.getId(), quantity);
        List<OrderLine> lines = List.of(new OrderLine(
                decreased.getId(),
                decreased.getProduct().getId(),
                decreased.getProduct().getName(),
                decreased.getPrice(),
                quantity
        ));
        com.loopers.domain.order.vo.Money original = new com.loopers.domain.order.vo.Money(
                lines.stream().mapToLong(OrderLine::amount).sum()
        );
        return orderService.placeOrder(new OrderModel(TEST_USER_ID, null), lines, original, new com.loopers.domain.order.vo.Money(0L));
    }

    @DisplayName("단건 주문 조회(getByUser) 시,")
    @Nested
    class GetByUser {

        @DisplayName("본인 주문이면, 주문이 반환된다.")
        @Test
        void returnsOrder_whenUserOwnsOrder() {
            OrderModel order = placeOrder(1);

            OrderModel result = orderService.getByUser(order.getId(), TEST_USER_ID);

            assertThat(result.getId()).isEqualTo(order.getId());
        }

        @DisplayName("타인의 주문이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenUserDoesNotOwnOrder() {
            OrderModel order = placeOrder(1);
            Long otherUserId = TEST_USER_ID + 1;

            CoreException exception = assertThrows(CoreException.class,
                    () -> orderService.getByUser(order.getId(), otherUserId));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("주문 완료 처리 시,")
    @Nested
    class Complete {

        @DisplayName("주문 요청 상태면, 완료 상태로 변경된다.")
        @Test
        void completesOrder_whenStatusIsRequested() {
            OrderModel order = placeOrder(1);

            orderService.complete(order.getId());

            OrderModel updated = orderRepository.findById(order.getId()).get();
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        }
    }

    @DisplayName("주문 취소 처리 시,")
    @Nested
    class Cancel {

        @DisplayName("주문 요청 상태면, 취소 상태로 변경된다.")
        @Test
        void cancelsOrder_whenStatusIsRequested() {
            OrderModel order = placeOrder(1);

            orderService.cancel(order.getId(), TEST_USER_ID);

            OrderModel updated = orderRepository.findById(order.getId()).get();
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @DisplayName("이미 완료된 주문은 취소할 수 없다.")
        @Test
        void throwsBadRequest_whenOrderIsAlreadyCompleted() {
            OrderModel order = placeOrder(1);
            orderService.complete(order.getId());

            CoreException exception = assertThrows(CoreException.class,
                    () -> orderService.cancel(order.getId(), TEST_USER_ID));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("타인의 주문이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenUserDoesNotOwnOrder() {
            OrderModel order = placeOrder(1);
            Long otherUserId = TEST_USER_ID + 1;

            CoreException exception = assertThrows(CoreException.class,
                    () -> orderService.cancel(order.getId(), otherUserId));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
