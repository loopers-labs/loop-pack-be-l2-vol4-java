package com.loopers.domain.order;

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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 100L;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    private OrderItemEntity item(Long productId, String name, Long price, int qty) {
        return new OrderItemEntity(productId, name, price, qty);
    }

    private OrderEntity savedOrder(Long id, Long userId, List<OrderItemEntity> items) {
        return OrderEntity.of(id, userId, OrderStatus.PENDING, items,
                ZonedDateTime.now(), ZonedDateTime.now(), null);
    }

    // ─────────────────────────────────────────────
    // createOrder
    // ─────────────────────────────────────────────

    @DisplayName("주문 생성")
    @Nested
    class CreateOrder {

        @DisplayName("[ECP] 유효한 항목으로 주문 생성 시 PENDING 상태의 OrderEntity가 저장된다.")
        @Test
        void savesOrder_withPendingStatus_whenItemsAreValid() {
            // arrange
            List<OrderItemEntity> items = List.of(item(1L, "에어맥스", 100_000L, 2));
            OrderEntity saved = savedOrder(ORDER_ID, USER_ID, items);
            given(orderRepository.save(any())).willReturn(saved);

            // act
            OrderEntity result = orderService.createOrder(USER_ID, items);

            // assert
            assertAll(
                    () -> assertNotNull(result.getId()),
                    () -> assertEquals(USER_ID, result.getUserId()),
                    () -> assertEquals(OrderStatus.PENDING, result.getStatus()),
                    () -> assertEquals(1, result.getItems().size())
            );
            verify(orderRepository).save(any());
        }

        @DisplayName("[ECP] 빈 항목 목록으로 주문 생성 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsAreEmpty() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> orderService.createOrder(USER_ID, List.of()));
            assertEquals(ErrorType.BAD_REQUEST, exception.getErrorType());
        }

        @DisplayName("[Error Guessing] null 항목으로 주문 생성 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsAreNull() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> orderService.createOrder(USER_ID, null));
            assertEquals(ErrorType.BAD_REQUEST, exception.getErrorType());
        }

        @DisplayName("[ECP] 중복된 productId가 포함된 항목으로 주문 생성 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDuplicateProductIdExists() {
            // arrange
            List<OrderItemEntity> items = List.of(
                    item(1L, "에어맥스", 100_000L, 1),
                    item(1L, "에어맥스", 100_000L, 2)
            );

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> orderService.createOrder(USER_ID, items));
            assertEquals(ErrorType.BAD_REQUEST, exception.getErrorType());
        }
    }

    // ─────────────────────────────────────────────
    // getOrder
    // ─────────────────────────────────────────────

    @DisplayName("주문 단건 조회")
    @Nested
    class GetOrder {

        @DisplayName("[ECP] 존재하는 orderId로 조회하면 OrderEntity를 반환한다.")
        @Test
        void returnsOrder_whenOrderExists() {
            // arrange
            List<OrderItemEntity> items = List.of(item(1L, "에어맥스", 100_000L, 1));
            OrderEntity order = savedOrder(ORDER_ID, USER_ID, items);
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

            // act
            OrderEntity result = orderService.getOrder(ORDER_ID);

            // assert
            assertAll(
                    () -> assertEquals(ORDER_ID, result.getId()),
                    () -> assertEquals(USER_ID, result.getUserId())
            );
        }

        @DisplayName("[ECP] 존재하지 않는 orderId로 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderNotExists() {
            // arrange
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.empty());

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> orderService.getOrder(ORDER_ID));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    // ─────────────────────────────────────────────
    // getOrders
    // ─────────────────────────────────────────────

    @DisplayName("주문 목록 조회 (Customer)")
    @Nested
    class GetOrders {

        @DisplayName("[ECP] 날짜 필터 없이 userId로 조회하면 해당 유저의 주문 목록이 반환된다.")
        @Test
        void returnsOrderPage_whenNoDateFilter() {
            // arrange
            PageRequest pageable = PageRequest.of(0, 20);
            List<OrderItemEntity> items = List.of(item(1L, "에어맥스", 100_000L, 1));
            List<OrderEntity> orders = List.of(
                    savedOrder(ORDER_ID, USER_ID, items),
                    savedOrder(ORDER_ID + 1, USER_ID, items)
            );
            given(orderRepository.findAllByUserId(USER_ID, null, null, pageable))
                    .willReturn(new PageImpl<>(orders, pageable, 2));

            // act
            Page<OrderEntity> result = orderService.getOrders(USER_ID, null, null, pageable);

            // assert
            assertAll(
                    () -> assertEquals(2, result.getTotalElements()),
                    () -> assertTrue(result.getContent().stream()
                            .allMatch(o -> o.getUserId().equals(USER_ID)))
            );
            verify(orderRepository).findAllByUserId(USER_ID, null, null, pageable);
        }

        @DisplayName("[ADR-010] startAt/endAt 날짜 필터를 전달하면 해당 Repository 메서드가 호출된다.")
        @Test
        void delegatesToRepository_whenDateFilterProvided() {
            // arrange
            PageRequest pageable = PageRequest.of(0, 20);
            ZonedDateTime startAt = ZonedDateTime.now().minusDays(7);
            ZonedDateTime endAt = ZonedDateTime.now();
            List<OrderItemEntity> items = List.of(item(1L, "에어맥스", 100_000L, 1));
            given(orderRepository.findAllByUserId(USER_ID, startAt, endAt, pageable))
                    .willReturn(new PageImpl<>(List.of(savedOrder(ORDER_ID, USER_ID, items)), pageable, 1));

            // act
            Page<OrderEntity> result = orderService.getOrders(USER_ID, startAt, endAt, pageable);

            // assert
            assertEquals(1, result.getTotalElements());
            verify(orderRepository).findAllByUserId(USER_ID, startAt, endAt, pageable);
        }

        @DisplayName("[ECP] 주문이 없는 유저 조회 시 빈 페이지가 반환된다.")
        @Test
        void returnsEmptyPage_whenNoOrdersExist() {
            // arrange
            PageRequest pageable = PageRequest.of(0, 20);
            given(orderRepository.findAllByUserId(USER_ID, null, null, pageable))
                    .willReturn(Page.empty(pageable));

            // act
            Page<OrderEntity> result = orderService.getOrders(USER_ID, null, null, pageable);

            // assert
            assertTrue(result.getContent().isEmpty());
        }
    }

    // ─────────────────────────────────────────────
    // getAllOrders
    // ─────────────────────────────────────────────

    @DisplayName("전체 주문 목록 조회 (Admin)")
    @Nested
    class GetAllOrders {

        @DisplayName("[ECP] 전체 주문 목록을 페이지로 반환한다.")
        @Test
        void returnsAllOrdersPage() {
            // arrange
            PageRequest pageable = PageRequest.of(0, 20);
            List<OrderItemEntity> items = List.of(item(1L, "에어맥스", 100_000L, 1));
            List<OrderEntity> orders = List.of(
                    savedOrder(ORDER_ID, USER_ID, items),
                    savedOrder(ORDER_ID + 1, 2L, items)
            );
            given(orderRepository.findAll(pageable))
                    .willReturn(new PageImpl<>(orders, pageable, 2));

            // act
            Page<OrderEntity> result = orderService.getAllOrders(pageable);

            // assert
            assertEquals(2, result.getTotalElements());
            verify(orderRepository).findAll(pageable);
        }

        @DisplayName("[ECP] 주문이 없으면 빈 페이지가 반환된다.")
        @Test
        void returnsEmptyPage_whenNoOrdersExist() {
            // arrange
            PageRequest pageable = PageRequest.of(0, 20);
            given(orderRepository.findAll(pageable)).willReturn(Page.empty(pageable));

            // act
            Page<OrderEntity> result = orderService.getAllOrders(pageable);

            // assert
            assertTrue(result.getContent().isEmpty());
        }
    }
}
