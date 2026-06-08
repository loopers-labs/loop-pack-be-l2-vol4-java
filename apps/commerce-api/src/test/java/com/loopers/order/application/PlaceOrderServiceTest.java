package com.loopers.order.application;

import com.loopers.brand.application.BrandReader;
import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderItem;
import com.loopers.order.domain.OrderItemRepository;
import com.loopers.order.domain.OrderRepository;
import com.loopers.order.domain.OrderStatus;
import com.loopers.payment.application.PaymentService;
import com.loopers.product.application.ProductInfo;
import com.loopers.product.application.ProductReader;
import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import com.loopers.product.domain.ProductErrorCode;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceOrderServiceTest {

    private static final Long USER_ID = 1L;

    private final UserReader userReader = mock(UserReader.class);
    private final ProductReader productReader = mock(ProductReader.class);
    private final ProductStockRepository productStockRepository = mock(ProductStockRepository.class);
    private final BrandReader brandReader = mock(BrandReader.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
    private final OrderNumberGenerator orderNumberGenerator = mock(OrderNumberGenerator.class);
    private final PaymentService paymentService = mock(PaymentService.class);

    private final PlaceOrderService placeOrderService = new PlaceOrderService(
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
        when(productReader.getInfo(productId)).thenReturn(new ProductInfo(name, brandId, price));
        when(brandReader.getName(brandId)).thenReturn("브랜드" + brandId);
        when(productStockRepository.findByProductIdForUpdate(productId))
                .thenReturn(Optional.of(ProductStock.create(productId, stockQty)));
    }

    @Test
    @DisplayName("주문 생성: 상품 조회 후 재고를 차감하고 PENDING 주문과 스냅샷 항목을 저장한다")
    void givenAvailableProducts_whenPlace_thenDecreasesStockAndSavesPendingOrder() {
        stubProduct(10L, 1L, "셔츠", 29_000L, 50);
        when(orderNumberGenerator.generate()).thenReturn("20260528-000001");
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResult.Detail result = placeOrderService.place(command(List.of(new OrderCommand.Line(10L, 2))));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertAll(
                () -> assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING),
                () -> assertThat(savedOrder.getOrderNumber()).isEqualTo("20260528-000001"),
                () -> assertThat(savedOrder.getTotalAmount().value()).isEqualTo(58_000L),
                () -> assertThat(result.totalAmount()).isEqualTo(58_000L),
                () -> assertThat(result.items()).hasSize(1),
                () -> assertThat(result.items().get(0).productName()).isEqualTo("셔츠"),
                () -> assertThat(result.items().get(0).brandName()).isEqualTo("브랜드1")
        );
    }

    @Test
    @DisplayName("주문 생성: 재고는 productId 오름차순으로 잠금을 획득한다 (deadlock 방지)")
    void givenItemsInDescendingProductId_whenPlace_thenLocksStockInAscendingOrder() {
        stubProduct(10L, 1L, "셔츠", 29_000L, 50);
        stubProduct(20L, 1L, "바지", 15_000L, 50);
        when(orderNumberGenerator.generate()).thenReturn("20260528-000001");
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        placeOrderService.place(command(List.of(
                new OrderCommand.Line(20L, 1),
                new OrderCommand.Line(10L, 1)
        )));

        InOrder inOrder = inOrder(productStockRepository);
        inOrder.verify(productStockRepository).findByProductIdForUpdate(10L);
        inOrder.verify(productStockRepository).findByProductIdForUpdate(20L);
    }

    @Test
    @DisplayName("주문 생성: 재고가 부족하면 CONFLICT 가 전파되고 주문을 저장하지 않는다")
    void givenInsufficientStock_whenPlace_thenThrowsConflictAndSavesNothing() {
        stubProduct(10L, 1L, "셔츠", 29_000L, 1);
        when(orderNumberGenerator.generate()).thenReturn("20260528-000001");

        assertThatThrownBy(() -> placeOrderService.place(command(List.of(new OrderCommand.Line(10L, 5)))))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.OUT_OF_STOCK);

        verify(orderRepository, never()).save(any());
        verify(orderItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("주문 생성: 삭제/판매중지/존재하지 않는 상품이면 NOT_FOUND 가 전파되고 주문을 저장하지 않는다")
    void givenInactiveProduct_whenPlace_thenThrowsNotFoundAndSavesNothing() {
        when(productReader.getInfo(99L))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        assertThatThrownBy(() -> placeOrderService.place(command(List.of(new OrderCommand.Line(99L, 1)))))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorType.NOT_FOUND);

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("주문 생성: 존재하지 않는 사용자면 NOT_FOUND 가 전파되고 재고·주문을 건드리지 않는다")
    void givenNonExistingUser_whenPlace_thenThrowsNotFoundAndTouchesNothing() {
        doThrow(new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."))
                .when(userReader).ensureExists(USER_ID);

        assertThatThrownBy(() -> placeOrderService.place(command(List.of(new OrderCommand.Line(10L, 1)))))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorType.NOT_FOUND);

        verify(productStockRepository, never()).findByProductIdForUpdate(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("주문 생성: 주문 저장 후 결제 통합 지점(PaymentService.pay)을 호출한다")
    void givenAvailableProducts_whenPlace_thenInvokesPaymentStubAfterSave() {
        stubProduct(10L, 1L, "셔츠", 29_000L, 50);
        when(orderNumberGenerator.generate()).thenReturn("20260528-000001");
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        placeOrderService.place(command(List.of(new OrderCommand.Line(10L, 1))));

        InOrder inOrder = inOrder(orderRepository, paymentService);
        inOrder.verify(orderRepository).save(any());
        inOrder.verify(paymentService).pay(any(), any());
    }

    @Test
    @DisplayName("주문 생성: 저장된 주문 항목에 주문 id 가 채워진다")
    void givenAvailableProducts_whenPlace_thenAssignsOrderIdToItems() {
        stubProduct(10L, 1L, "셔츠", 29_000L, 50);
        when(orderNumberGenerator.generate()).thenReturn("20260528-000001");
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        placeOrderService.place(command(List.of(new OrderCommand.Line(10L, 1))));

        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getOrderId()).isNotNull();
    }
}
