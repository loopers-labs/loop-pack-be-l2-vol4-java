package com.loopers.order.application;

import com.loopers.brand.application.BrandReader;
import com.loopers.brand.domain.Brand;
import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderItem;
import com.loopers.order.domain.OrderItemRepository;
import com.loopers.order.domain.OrderRepository;
import com.loopers.order.domain.OrderStatus;
import com.loopers.product.application.ProductReader;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.user.application.UserReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private static final Long USER_ID = 1L;

    private final UserReader userReader = mock(UserReader.class);
    private final ProductReader productReader = mock(ProductReader.class);
    private final ProductStockRepository productStockRepository = mock(ProductStockRepository.class);
    private final BrandReader brandReader = mock(BrandReader.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
    private final OrderNumberGenerator orderNumberGenerator = mock(OrderNumberGenerator.class);
    private final PaymentService paymentService = mock(PaymentService.class);

    private final OrderService orderService = new OrderService(
            userReader, productReader, productStockRepository, brandReader,
            orderRepository, orderItemRepository, orderNumberGenerator, paymentService
    );

    private OrderCommand.Create command(List<OrderCommand.Line> lines) {
        return new OrderCommand.Create(
                USER_ID, lines,
                "김루퍼", "010-1234-5678", "12345", "서울시 강남구", "101동"
        );
    }

    private void stubProduct(Long productId, Long brandId, String name, long price, int stockQty) {
        Product product = Product.create(brandId, name, "설명", price, "thumb.jpg");
        when(productReader.getActive(productId)).thenReturn(product);
        when(brandReader.get(brandId)).thenReturn(Brand.create("브랜드" + brandId, "설명", null));
        when(productStockRepository.findByProductIdForUpdate(productId))
                .thenReturn(Optional.of(ProductStock.create(productId, stockQty)));
    }

    @Test
    @DisplayName("주문 생성: 상품 조회 후 재고를 차감하고 PENDING 주문과 스냅샷 항목을 저장한다")
    void givenAvailableProducts_whenCreate_thenDecreasesStockAndSavesPendingOrder() {
        stubProduct(10L, 1L, "셔츠", 29_000L, 50);
        when(orderNumberGenerator.generate()).thenReturn("20260528-000001");
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResult.Detail result = orderService.create(command(List.of(new OrderCommand.Line(10L, 2))));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertAll(
                () -> assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING),
                () -> assertThat(savedOrder.getOrderNumber()).isEqualTo("20260528-000001"),
                () -> assertThat(savedOrder.getTotalAmount()).isEqualTo(58_000L),
                () -> assertThat(result.totalAmount()).isEqualTo(58_000L),
                () -> assertThat(result.items()).hasSize(1),
                () -> assertThat(result.items().get(0).productName()).isEqualTo("셔츠"),
                () -> assertThat(result.items().get(0).brandName()).isEqualTo("브랜드1")
        );
    }

    @Test
    @DisplayName("주문 생성: 재고는 productId 오름차순으로 잠금을 획득한다 (deadlock 방지)")
    void givenItemsInDescendingProductId_whenCreate_thenLocksStockInAscendingOrder() {
        stubProduct(10L, 1L, "셔츠", 29_000L, 50);
        stubProduct(20L, 1L, "바지", 15_000L, 50);
        when(orderNumberGenerator.generate()).thenReturn("20260528-000001");
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.create(command(List.of(
                new OrderCommand.Line(20L, 1),
                new OrderCommand.Line(10L, 1)
        )));

        InOrder inOrder = inOrder(productStockRepository);
        inOrder.verify(productStockRepository).findByProductIdForUpdate(10L);
        inOrder.verify(productStockRepository).findByProductIdForUpdate(20L);
    }

    @Test
    @DisplayName("주문 생성: 재고가 부족하면 CONFLICT 가 전파되고 주문을 저장하지 않는다")
    void givenInsufficientStock_whenCreate_thenThrowsConflictAndSavesNothing() {
        stubProduct(10L, 1L, "셔츠", 29_000L, 1);
        when(orderNumberGenerator.generate()).thenReturn("20260528-000001");

        assertThatThrownBy(() -> orderService.create(command(List.of(new OrderCommand.Line(10L, 5)))))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.CONFLICT);

        verify(orderRepository, never()).save(any());
        verify(orderItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("주문 생성: 삭제/판매중지/존재하지 않는 상품이면 NOT_FOUND 가 전파되고 주문을 저장하지 않는다")
    void givenInactiveProduct_whenCreate_thenThrowsNotFoundAndSavesNothing() {
        when(productReader.getActive(99L))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        assertThatThrownBy(() -> orderService.create(command(List.of(new OrderCommand.Line(99L, 1)))))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("주문 생성: 존재하지 않는 사용자면 NOT_FOUND 가 전파되고 재고·주문을 건드리지 않는다")
    void givenNonExistingUser_whenCreate_thenThrowsNotFoundAndTouchesNothing() {
        when(userReader.get(USER_ID))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        assertThatThrownBy(() -> orderService.create(command(List.of(new OrderCommand.Line(10L, 1)))))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);

        verify(productStockRepository, never()).findByProductIdForUpdate(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("주문 생성: 주문 저장 후 결제 통합 지점(PaymentService.pay)을 호출한다")
    void givenAvailableProducts_whenCreate_thenInvokesPaymentStubAfterSave() {
        stubProduct(10L, 1L, "셔츠", 29_000L, 50);
        when(orderNumberGenerator.generate()).thenReturn("20260528-000001");
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.create(command(List.of(new OrderCommand.Line(10L, 1))));

        InOrder inOrder = inOrder(orderRepository, paymentService);
        inOrder.verify(orderRepository).save(any());
        inOrder.verify(paymentService).pay(any());
    }

    @Test
    @DisplayName("주문 생성: 저장된 주문 항목에 주문 id 가 채워진다")
    void givenAvailableProducts_whenCreate_thenAssignsOrderIdToItems() {
        stubProduct(10L, 1L, "셔츠", 29_000L, 50);
        when(orderNumberGenerator.generate()).thenReturn("20260528-000001");
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.create(command(List.of(new OrderCommand.Line(10L, 1))));

        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getOrderId()).isNotNull();
    }

    @Test
    @DisplayName("내 주문 조회: 사용자의 주문을 주문 단위 요약으로 반환한다")
    void givenUserOrders_whenGetMyOrders_thenReturnsOrderSummaries() {
        Order order = Order.create(USER_ID, "20260528-000001",
                com.loopers.order.domain.ShippingDestination.create("김루퍼", "010-1234-5678", "12345", "서울", "101"),
                List.of(OrderItem.create(10L, "셔츠", 1L, "루퍼스", 29_000L, 1)));
        when(orderRepository.findByUserId(USER_ID)).thenReturn(List.of(order));

        List<OrderResult.Summary> result = orderService.getMyOrders(USER_ID);

        assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).orderNumber()).isEqualTo("20260528-000001"),
                () -> assertThat(result.get(0).status()).isEqualTo(OrderStatus.PENDING),
                () -> assertThat(result.get(0).totalAmount()).isEqualTo(29_000L)
        );
    }

    @Test
    @DisplayName("내 주문 조회: 주문이 없으면 빈 리스트를 반환한다")
    void givenNoOrders_whenGetMyOrders_thenReturnsEmpty() {
        when(orderRepository.findByUserId(USER_ID)).thenReturn(List.of());

        List<OrderResult.Summary> result = orderService.getMyOrders(USER_ID);

        assertThat(result).isEmpty();
    }
}
