package com.loopers.application.order;

import com.loopers.domain.inventory.InventoryEntity;
import com.loopers.domain.inventory.InventoryService;
import com.loopers.domain.order.OrderEntity;
import com.loopers.domain.order.OrderItemEntity;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.ProductEntity;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long ORDER_ID = 100L;
    private static final Long PRODUCT_ID = 10L;

    @Mock
    private OrderService orderService;

    @Mock
    private ProductService productService;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private OrderFacade orderFacade;

    private ProductEntity productOf(Long id, String name, Long price) {
        return ProductEntity.of(id, 1L, name, "설명", price, 0L,
                ZonedDateTime.now(), ZonedDateTime.now(), null);
    }

    private InventoryEntity inventoryOf(Long productId, Integer quantity) {
        return InventoryEntity.of(productId, productId, quantity,
                ZonedDateTime.now(), ZonedDateTime.now(), null);
    }

    private OrderEntity orderOf(Long id, Long userId, List<OrderItemEntity> items) {
        return OrderEntity.of(id, userId, OrderStatus.PENDING, items,
                ZonedDateTime.now(), ZonedDateTime.now(), null);
    }

    private OrderItemEntity itemOf(Long productId, String name, Long price, int qty) {
        return new OrderItemEntity(productId, name, price, qty);
    }

    // ─────────────────────────────────────────────
    // createOrder
    // ─────────────────────────────────────────────

    @DisplayName("주문 생성")
    @Nested
    class CreateOrder {

        @DisplayName("[ECP] 유효한 요청으로 주문 생성 시 PENDING 상태의 OrderInfo를 반환한다.")
        @Test
        void returnsOrderInfo_whenRequestIsValid() {
            // arrange
            List<OrderItemCommand> commands = List.of(new OrderItemCommand(PRODUCT_ID, 2));
            ProductEntity product = productOf(PRODUCT_ID, "에어맥스", 100_000L);
            InventoryEntity inventory = inventoryOf(PRODUCT_ID, 10);
            List<OrderItemEntity> items = List.of(itemOf(PRODUCT_ID, "에어맥스", 100_000L, 2));
            OrderEntity saved = orderOf(ORDER_ID, USER_ID, items);

            given(productService.getProduct(PRODUCT_ID)).willReturn(product);
            given(inventoryService.getByProductId(PRODUCT_ID)).willReturn(inventory);
            given(orderService.createOrder(any(), any())).willReturn(saved);

            // act
            OrderInfo result = orderFacade.createOrder(USER_ID, commands);

            // assert
            assertAll(
                    () -> assertEquals(ORDER_ID, result.orderId()),
                    () -> assertEquals(OrderStatus.PENDING, result.status()),
                    () -> assertEquals(200_000L, result.totalAmount()),
                    () -> assertEquals(1, result.items().size()),
                    () -> assertEquals(PRODUCT_ID, result.items().get(0).productId())
            );
        }

        @DisplayName("[ECP] 존재하지 않는 productId가 포함된 경우 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // arrange
            List<OrderItemCommand> commands = List.of(new OrderItemCommand(PRODUCT_ID, 1));
            given(productService.getProduct(PRODUCT_ID))
                    .willThrow(new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> orderFacade.createOrder(USER_ID, commands));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }

        @DisplayName("[ECP] 재고 부족 시 (fast-fail) BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenInventoryIsInsufficient_fastFail() {
            // arrange
            List<OrderItemCommand> commands = List.of(new OrderItemCommand(PRODUCT_ID, 5));
            ProductEntity product = productOf(PRODUCT_ID, "에어맥스", 100_000L);
            InventoryEntity inventory = inventoryOf(PRODUCT_ID, 3); // 요청 5 > 재고 3

            given(productService.getProduct(PRODUCT_ID)).willReturn(product);
            given(inventoryService.getByProductId(PRODUCT_ID)).willReturn(inventory);

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> orderFacade.createOrder(USER_ID, commands));
            assertEquals(ErrorType.BAD_REQUEST, exception.getErrorType());
        }

        @DisplayName("[ECP] FOR UPDATE 재고 차감 시 재고 부족이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenInventoryDeductionFails() {
            // arrange
            List<OrderItemCommand> commands = List.of(new OrderItemCommand(PRODUCT_ID, 2));
            ProductEntity product = productOf(PRODUCT_ID, "에어맥스", 100_000L);
            InventoryEntity inventory = inventoryOf(PRODUCT_ID, 10);
            List<OrderItemEntity> items = List.of(itemOf(PRODUCT_ID, "에어맥스", 100_000L, 2));
            OrderEntity saved = orderOf(ORDER_ID, USER_ID, items);

            given(productService.getProduct(PRODUCT_ID)).willReturn(product);
            given(inventoryService.getByProductId(PRODUCT_ID)).willReturn(inventory);
            given(orderService.createOrder(any(), any())).willReturn(saved);
            willThrow(new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다."))
                    .given(inventoryService).deductAll(any(Map.class));

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> orderFacade.createOrder(USER_ID, commands));
            assertEquals(ErrorType.BAD_REQUEST, exception.getErrorType());
        }
    }

    // ─────────────────────────────────────────────
    // getOrder
    // ─────────────────────────────────────────────

    @DisplayName("주문 단건 조회")
    @Nested
    class GetOrder {

        @DisplayName("[ECP] 본인의 주문을 조회하면 OrderInfo를 반환한다.")
        @Test
        void returnsOrderInfo_whenOrderIsOwnedByUser() {
            // arrange
            List<OrderItemEntity> items = List.of(itemOf(PRODUCT_ID, "에어맥스", 100_000L, 1));
            OrderEntity order = orderOf(ORDER_ID, USER_ID, items);
            given(orderService.getOrder(ORDER_ID)).willReturn(order);

            // act
            OrderInfo result = orderFacade.getOrder(USER_ID, ORDER_ID);

            // assert
            assertAll(
                    () -> assertEquals(ORDER_ID, result.orderId()),
                    () -> assertEquals(OrderStatus.PENDING, result.status())
            );
        }

        @DisplayName("[ECP] 존재하지 않는 orderId 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderNotExists() {
            // arrange
            given(orderService.getOrder(ORDER_ID))
                    .willThrow(new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> orderFacade.getOrder(USER_ID, ORDER_ID));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }

        @DisplayName("[ADR-017] 타인의 주문 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderIsOwnedByOtherUser() {
            // arrange
            List<OrderItemEntity> items = List.of(itemOf(PRODUCT_ID, "에어맥스", 100_000L, 1));
            OrderEntity order = orderOf(ORDER_ID, OTHER_USER_ID, items); // 타인 소유
            given(orderService.getOrder(ORDER_ID)).willReturn(order);

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> orderFacade.getOrder(USER_ID, ORDER_ID));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    // ─────────────────────────────────────────────
    // getOrders
    // ─────────────────────────────────────────────

    @DisplayName("주문 목록 조회")
    @Nested
    class GetOrders {

        @DisplayName("[ECP] 날짜 필터 없이 조회하면 전체 주문 목록 페이지를 반환한다.")
        @Test
        void returnsOrderPage_whenNoDateFilter() {
            // arrange
            PageRequest pageable = PageRequest.of(0, 20);
            List<OrderItemEntity> items = List.of(itemOf(PRODUCT_ID, "에어맥스", 100_000L, 1));
            List<OrderEntity> orders = List.of(
                    orderOf(ORDER_ID, USER_ID, items),
                    orderOf(ORDER_ID + 1, USER_ID, items)
            );
            given(orderService.getOrders(USER_ID, null, null, pageable))
                    .willReturn(new PageImpl<>(orders, pageable, 2));

            // act
            Page<OrderInfo> result = orderFacade.getOrders(USER_ID, null, null, pageable);

            // assert
            assertAll(
                    () -> assertEquals(2, result.getTotalElements()),
                    () -> assertTrue(result.getContent().stream()
                            .allMatch(o -> o.orderId() != null))
            );
        }

        @DisplayName("[ADR-010] startAt/endAt 기간 필터를 전달하면 기간 내 주문만 반환한다.")
        @Test
        void returnsFilteredOrderPage_whenDateFilterProvided() {
            // arrange
            PageRequest pageable = PageRequest.of(0, 20);
            ZonedDateTime startAt = ZonedDateTime.now().minusDays(7);
            ZonedDateTime endAt = ZonedDateTime.now();
            List<OrderItemEntity> items = List.of(itemOf(PRODUCT_ID, "에어맥스", 100_000L, 1));
            List<OrderEntity> orders = List.of(orderOf(ORDER_ID, USER_ID, items));
            given(orderService.getOrders(USER_ID, startAt, endAt, pageable))
                    .willReturn(new PageImpl<>(orders, pageable, 1));

            // act
            Page<OrderInfo> result = orderFacade.getOrders(USER_ID, startAt, endAt, pageable);

            // assert
            assertEquals(1, result.getTotalElements());
            verify(orderService).getOrders(USER_ID, startAt, endAt, pageable);
        }
    }
}
