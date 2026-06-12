package com.loopers.tddstudy.application.order;

import com.loopers.tddstudy.domain.order.Order;
import com.loopers.tddstudy.domain.product.Product;
import com.loopers.tddstudy.support.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class OrderServiceTest {

    private FakeOrderRepository fakeOrderRepository;
    private FakeProductRepository fakeProductRepository;
    private FakePaymentGateway fakePaymentGateway;
    private OrderService orderService;
    private FakeCouponRepository fakeCouponRepository;
    private FakeUserCouponRepository fakeUserCouponRepository;

    private Product product1;
    private Product product2;

    @BeforeEach
    void setUp() {
        fakeOrderRepository = new FakeOrderRepository();
        fakeProductRepository = new FakeProductRepository();
        fakePaymentGateway = new FakePaymentGateway();
        fakeCouponRepository = new FakeCouponRepository();
        fakeUserCouponRepository = new FakeUserCouponRepository();

        orderService = new OrderService(
                fakeOrderRepository,
                fakeProductRepository,
                fakePaymentGateway,
                fakeCouponRepository,
                fakeUserCouponRepository
        );

        product1 = fakeProductRepository.save(new Product("나이키 운동화", 50000, 10, 1L));
        product1.publish();
        fakeProductRepository.save(product1);

        product2 = fakeProductRepository.save(new Product("나이키 티셔츠", 30000, 5, 1L));
        product2.publish();
        fakeProductRepository.save(product2);
    }

    @Test
    @DisplayName("결제 성공 시 주문 상태가 PAID 가 되고 재고가 차감된다")
    void create_order_payment_success() {
        List<OrderItemRequest> items = List.of(
                new OrderItemRequest(product1.getId(), 2),
                new OrderItemRequest(product2.getId(), 1)
        );

        Order order = orderService.createOrder(1L, items, null);

        assertThat(order.getStatus()).isEqualTo("PAID");
        assertThat(order.getTotalAmount()).isEqualTo(130000);
        assertThat(fakeProductRepository.findById(product1.getId()).get().getStock()).isEqualTo(8);
        assertThat(fakeProductRepository.findById(product2.getId()).get().getStock()).isEqualTo(4);
    }

    @Test
    @DisplayName("결제 실패 시 재고가 복구되고 주문 상태가 FAILED 가 된다")
    void create_order_payment_fail_restores_stock() {
        fakePaymentGateway.willReturn("FAIL");
        List<OrderItemRequest> items = List.of(
                new OrderItemRequest(product1.getId(), 2)
        );

        Order order = orderService.createOrder(1L, items,null);

        assertThat(order.getStatus()).isEqualTo("FAILED");
        assertThat(fakeProductRepository.findById(product1.getId()).get().getStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("결제 타임아웃 시 재고가 복구되고 주문 상태가 FAILED 가 된다")
    void create_order_payment_timeout_restores_stock() {
        fakePaymentGateway.willReturn("TIMEOUT");
        List<OrderItemRequest> items = List.of(
                new OrderItemRequest(product1.getId(), 2)
        );

        Order order = orderService.createOrder(1L, items, null);

        assertThat(order.getStatus()).isEqualTo("FAILED");
        assertThat(fakeProductRepository.findById(product1.getId()).get().getStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("재고 부족 시 주문 전체가 실패하고 어떤 재고도 차감되지 않는다")
    void create_order_insufficient_stock_no_deduction() {
        List<OrderItemRequest> items = List.of(
                new OrderItemRequest(product1.getId(), 2),
                new OrderItemRequest(product2.getId(), 99)
        );

        assertThatThrownBy(() -> orderService.createOrder(1L, items,null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("재고가 부족합니다.");

        assertThat(fakeProductRepository.findById(product1.getId()).get().getStock()).isEqualTo(10);
        assertThat(fakeProductRepository.findById(product2.getId()).get().getStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("존재하지 않는 상품으로 주문 시 예외가 발생한다")
    void create_order_nonexistent_product_throws_exception() {
        List<OrderItemRequest> items = List.of(
                new OrderItemRequest(999L, 1)
        );

        assertThatThrownBy(() -> orderService.createOrder(1L, items, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("상품을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("본인 주문을 조회할 수 있다")
    void get_order_by_owner_success() {
        List<OrderItemRequest> items = List.of(new OrderItemRequest(product1.getId(), 1));
        Order saved = orderService.createOrder(1L, items,null);

        Order found = orderService.getOrder(1L, saved.getId());

        assertThat(found.getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("타인의 주문 조회 시 예외가 발생한다")
    void get_order_by_other_user_throws_exception() {
        List<OrderItemRequest> items = List.of(new OrderItemRequest(product1.getId(), 1));
        Order saved = orderService.createOrder(1L, items,null);

        assertThatThrownBy(() -> orderService.getOrder(2L, saved.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주문에 접근할 수 없습니다.");
    }

    @Test
    @DisplayName("내 주문 목록을 조회할 수 있다")
    void get_my_orders_success() {
        List<OrderItemRequest> items = List.of(new OrderItemRequest(product1.getId(), 1));
        orderService.createOrder(1L, items,null);
        orderService.createOrder(1L, items,null);

        List<Order> orders = orderService.getMyOrders(1L);

        assertThat(orders).hasSize(2);
    }

    @Test
    @DisplayName("주문 항목이 비어있으면 예외가 발생한다")
    void create_order_empty_items_throws_exception() {
        assertThatThrownBy(() -> orderService.createOrder(1L, List.of(),null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주문 항목이 비어있습니다.");
    }

}
