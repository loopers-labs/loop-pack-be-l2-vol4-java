package com.loopers.application.order;

import com.loopers.domain.order.FakeOrderRepository;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.FakeProductRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 주문 유스케이스(Application Layer) 단위 테스트.
 * Fake 저장소를 주입해 상품 조회 → 재고 차감 → 저장 흐름과 예외 흐름을 검증한다.
 */
class OrderFacadeTest {

    private static final Long USER_ID = 1L;

    private ProductRepository productRepository;
    private OrderRepository orderRepository;
    private OrderFacade orderFacade;

    @BeforeEach
    void setUp() {
        productRepository = new FakeProductRepository();
        orderRepository = new FakeOrderRepository();
        ProductService productService = new ProductService(productRepository);
        OrderService orderService = new OrderService();
        orderFacade = new OrderFacade(orderService, productService, orderRepository);
    }

    private Product saveProduct(String name, long price, int stock) {
        return productRepository.save(Product.create(name, "설명", Money.of(price), stock, 1L));
    }

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class CreateOrder {

        @DisplayName("정상 주문이면, 재고가 차감되고 총액이 계산된 주문이 저장된다.")
        @Test
        void createsAndSavesOrder_whenValid() {
            // arrange
            Product productA = saveProduct("상품A", 1_500L, 10);
            Product productB = saveProduct("상품B", 1_000L, 5);
            List<OrderItemCommand> items = List.of(
                new OrderItemCommand(productA.getId(), 2),
                new OrderItemCommand(productB.getId(), 1)
            );

            // act
            OrderInfo result = orderFacade.createOrder(USER_ID, items);

            // assert
            assertThat(result.orderId()).isNotNull();
            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.totalPrice()).isEqualTo(4_000L); // 1500*2 + 1000*1
            assertThat(result.items()).hasSize(2);
            // 재고 차감이 저장소에 반영됨
            assertThat(productRepository.find(productA.getId()).orElseThrow().getStock()).isEqualTo(8);
            assertThat(productRepository.find(productB.getId()).orElseThrow().getStock()).isEqualTo(4);
            // 주문이 실제로 저장됨
            assertThat(orderRepository.find(result.orderId())).isPresent();
        }

        @DisplayName("존재하지 않는 상품을 주문하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            List<OrderItemCommand> items = List.of(new OrderItemCommand(999L, 1));

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(USER_ID, items));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("재고가 부족하면, CONFLICT 예외가 발생하고 주문은 저장되지 않는다.")
        @Test
        void throwsConflict_andDoesNotSave_whenStockIsNotEnough() {
            // arrange
            Product product = saveProduct("상품A", 1_000L, 1);
            List<OrderItemCommand> items = List.of(new OrderItemCommand(product.getId(), 2));

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(USER_ID, items));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            assertThat(orderRepository.findByUserId(USER_ID)).isEmpty();
        }

        @DisplayName("로그인 유저 정보가 없으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenUserIsNull() {
            Product product = saveProduct("상품A", 1_000L, 10);
            List<OrderItemCommand> items = List.of(new OrderItemCommand(product.getId(), 1));

            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(null, items));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("주문 항목이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsAreEmpty() {
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(USER_ID, List.of()));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
