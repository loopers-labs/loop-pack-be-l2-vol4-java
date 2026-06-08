package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderDetail;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderSummary;
import com.loopers.domain.order.model.OrderItemStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderV1ControllerTest {

    private OrderFacade orderFacade;
    private OrderV1Controller orderV1Controller;

    @BeforeEach
    void setUp() {
        orderFacade = mock(OrderFacade.class);
        orderV1Controller = new OrderV1Controller(orderFacade);
    }

    @DisplayName("주문 생성 시, ")
    @Nested
    class CreateOrder {

        @DisplayName("쿠폰 없이 정상 요청이면, 생성된 orderId를 반환한다.")
        @Test
        void returnsOrderId_whenRequestIsValid() {
            when(orderFacade.createOrder(eq("user1"), any(), eq(null))).thenReturn(42L);
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.CreateRequest.OrderItemDto(1L, 3)), null
            );

            var result = orderV1Controller.createOrder("user1", request);

            assertThat(result.data().orderId()).isEqualTo(42L);
        }

        @DisplayName("존재하지 않는 회원이면, NOT_FOUND 예외가 전파된다.")
        @Test
        void propagatesNotFound_whenMemberDoesNotExist() {
            when(orderFacade.createOrder(eq("unknown"), any(), any()))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 회원입니다."));
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.CreateRequest.OrderItemDto(1L, 1)), null
            );

            CoreException ex = assertThrows(CoreException.class,
                () -> orderV1Controller.createOrder("unknown", request)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("재고 부족이면, BAD_REQUEST 예외가 전파된다.")
        @Test
        void propagatesBadRequest_whenStockIsInsufficient() {
            when(orderFacade.createOrder(eq("user1"), any(), any()))
                .thenThrow(new CoreException(ErrorType.BAD_REQUEST, "재고가 부족한 상품이 있습니다."));
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.CreateRequest.OrderItemDto(1L, 999)), null
            );

            CoreException ex = assertThrows(CoreException.class,
                () -> orderV1Controller.createOrder("user1", request)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("주문 목록 조회 시, ")
    @Nested
    class GetOrders {

        @DisplayName("정상 요청이면, 주문 요약 목록을 반환한다.")
        @Test
        void returnsOrderSummaries_whenRequestIsValid() {
            ZonedDateTime now = ZonedDateTime.now();
            OrderSummary summary = new OrderSummary(10L, 100_000L, 0L, 100_000L, now);
            Page<OrderSummary> page = new PageImpl<>(List.of(summary));
            when(orderFacade.getOrders(eq("user1"), any(), any(), anyInt(), anyInt())).thenReturn(page);

            var result = orderV1Controller.getOrders("user1", null, null, 0, 20);

            assertThat(result.data().getContent()).hasSize(1);
            assertThat(result.data().getContent().get(0).orderId()).isEqualTo(10L);
        }
    }

    @DisplayName("주문 상세 조회 시, ")
    @Nested
    class GetOrder {

        @DisplayName("본인 주문이면, 상세 정보를 반환한다.")
        @Test
        void returnsOrderDetail_whenOrderBelongsToMember() {
            ZonedDateTime now = ZonedDateTime.now();
            OrderDetail.OrderItemInfo itemInfo = new OrderDetail.OrderItemInfo(
                "에어맥스", "나이키", 50_000L, 2, OrderItemStatus.ORDERED
            );
            OrderDetail detail = new OrderDetail(10L, 100_000L, 0L, 100_000L, now, List.of(itemInfo));
            when(orderFacade.getOrder("user1", 10L)).thenReturn(detail);

            var result = orderV1Controller.getOrder("user1", 10L);

            assertThat(result.data().orderId()).isEqualTo(10L);
            assertThat(result.data().items()).hasSize(1);
            assertThat(result.data().items().get(0).productName()).isEqualTo("에어맥스");
        }

        @DisplayName("타인의 주문이면, FORBIDDEN 예외가 전파된다.")
        @Test
        void propagatesForbidden_whenOrderBelongsToOtherMember() {
            when(orderFacade.getOrder("user1", 10L))
                .thenThrow(new CoreException(ErrorType.FORBIDDEN, "접근 권한이 없습니다."));

            CoreException ex = assertThrows(CoreException.class,
                () -> orderV1Controller.getOrder("user1", 10L)
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }
    }
}
