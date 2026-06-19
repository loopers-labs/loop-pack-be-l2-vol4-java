package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private OrderService orderService;
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderService = new OrderService(orderRepository);
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 입력이면 originalPrice가 계산된 OrderModel이 반환된다.")
        @Test
        void returnsOrderModel_withCalculatedOriginalPrice() {
            // given
            Long userId = 1L;
            List<OrderItemData> itemDataList = List.of(
                    new OrderItemData(1L, "상품A", BigDecimal.valueOf(10000), 2L),
                    new OrderItemData(2L, "상품B", BigDecimal.valueOf(5000), 1L)
            );
            BigDecimal expectedTotal = BigDecimal.valueOf(25000);

            OrderItemModel itemA = new OrderItemModel(1L, "상품A", BigDecimal.valueOf(10000), 2L);
            OrderItemModel itemB = new OrderItemModel(2L, "상품B", BigDecimal.valueOf(5000), 1L);
            OrderModel saved = new OrderModel(userId, expectedTotal, BigDecimal.ZERO, List.of(itemA, itemB));
            when(orderRepository.save(any(OrderModel.class))).thenReturn(saved);

            // when
            OrderModel result = orderService.create(userId, itemDataList, BigDecimal.ZERO);

            // then
            assertAll(
                    () -> assertThat(result.getUserId()).isEqualTo(userId),
                    () -> assertThat(result.getOriginalPrice()).isEqualByComparingTo(expectedTotal),
                    () -> assertThat(result.getStatus()).isEqualTo(OrderStatus.PLACED)
            );
        }
    }

    @DisplayName("주문을 ID로 조회할 때,")
    @Nested
    class GetById {

        @DisplayName("존재하는 ID이면 OrderModel이 반환된다.")
        @Test
        void returnsOrderModel_whenOrderExists() {
            // given
            Long orderId = 1L;
            OrderModel order = new OrderModel(1L, BigDecimal.valueOf(10000), BigDecimal.ZERO,
                    List.of(new OrderItemModel(1L, "상품", BigDecimal.valueOf(10000), 1L)));
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            // when
            OrderModel result = orderService.getById(orderId);

            // then
            assertThat(result).isSameAs(order);
        }

        @DisplayName("존재하지 않는 ID이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenOrderDoesNotExist() {
            // given
            Long orderId = 999L;
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class, () -> orderService.getById(orderId));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("유저 ID로 기간별 주문 목록을 조회할 때,")
    @Nested
    class GetOrdersByUserIdBetween {

        @DisplayName("해당 기간의 주문 목록이 반환된다.")
        @Test
        void returnsOrderList_forGivenPeriod() {
            // given
            Long userId = 1L;
            LocalDate startAt = LocalDate.now().minusDays(7);
            LocalDate endAt = LocalDate.now();
            ZoneId zone = ZoneId.of("Asia/Seoul");
            ZonedDateTime from = startAt.atStartOfDay(zone);
            ZonedDateTime to = endAt.plusDays(1).atStartOfDay(zone);
            OrderModel order = new OrderModel(userId, BigDecimal.valueOf(10000), BigDecimal.ZERO,
                    List.of(new OrderItemModel(1L, "상품", BigDecimal.valueOf(10000), 1L)));
            when(orderRepository.findAllByUserIdAndCreatedAtBetween(userId, from, to))
                    .thenReturn(List.of(order));

            // when
            List<OrderModel> result = orderService.getOrdersByUserIdBetween(userId, startAt, endAt);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isSameAs(order);
        }

        @DisplayName("해당 기간에 주문이 없으면 빈 목록이 반환된다.")
        @Test
        void returnsEmptyList_whenNoOrdersInPeriod() {
            // given
            Long userId = 1L;
            LocalDate startAt = LocalDate.now().minusDays(7);
            LocalDate endAt = LocalDate.now();
            ZoneId zone = ZoneId.of("Asia/Seoul");
            ZonedDateTime from = startAt.atStartOfDay(zone);
            ZonedDateTime to = endAt.plusDays(1).atStartOfDay(zone);
            when(orderRepository.findAllByUserIdAndCreatedAtBetween(userId, from, to))
                    .thenReturn(List.of());

            // when
            List<OrderModel> result = orderService.getOrdersByUserIdBetween(userId, startAt, endAt);

            // then
            assertThat(result).isEmpty();
        }

        @DisplayName("시작일이 종료일보다 이후이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenStartAtIsAfterEndAt() {
            // given
            Long userId = 1L;
            LocalDate startAt = LocalDate.now();
            LocalDate endAt = LocalDate.now().minusDays(1);

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> orderService.getOrdersByUserIdBetween(userId, startAt, endAt));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("items를 포함하여 주문을 ID로 조회할 때,")
    @Nested
    class GetByIdWithItems {

        @DisplayName("존재하는 ID이면 OrderModel이 반환된다.")
        @Test
        void returnsOrderModel_whenOrderExists() {
            // given
            Long orderId = 1L;
            OrderModel order = new OrderModel(1L, BigDecimal.valueOf(10000), BigDecimal.ZERO,
                    List.of(new OrderItemModel(1L, "상품", BigDecimal.valueOf(10000), 1L)));
            when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));

            // when
            OrderModel result = orderService.getByIdWithItems(orderId);

            // then
            assertThat(result).isSameAs(order);
        }

        @DisplayName("존재하지 않는 ID이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenOrderDoesNotExist() {
            // given
            Long orderId = 999L;
            when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class, () -> orderService.getByIdWithItems(orderId));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("전체 주문 목록을 페이지 조회할 때,")
    @Nested
    class FindAll {

        @DisplayName("주문이 있으면 페이지 목록이 반환된다.")
        @Test
        void returnsOrderPage_whenOrdersExist() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            OrderModel order = new OrderModel(1L, BigDecimal.valueOf(10000), BigDecimal.ZERO,
                    List.of(new OrderItemModel(1L, "상품", BigDecimal.valueOf(10000), 1L)));
            Page<OrderModel> page = new PageImpl<>(List.of(order));
            when(orderRepository.findAll(pageable)).thenReturn(page);

            // when
            Page<OrderModel> result = orderService.findAll(pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
        }
    }
}
