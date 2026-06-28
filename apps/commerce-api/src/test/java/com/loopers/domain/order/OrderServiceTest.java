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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private OrderService orderService;

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정상 lines 가 주어지면, OrderModel 과 OrderItem 이 모두 저장되고 OrderResult 로 반환된다.")
        @Test
        void persistsOrderAndItems_whenLinesAreValid() {
            // given
            Long userId = 1L;
            List<OrderLine> rawLines = List.of(
                new OrderLine(100L, 2, "에어맥스 270", 100_000L, "나이키"),
                new OrderLine(200L, 3, "스탠스미스", 50_000L, "아디다스")
            );
            given(orderRepository.save(any(OrderModel.class))).willAnswer(invocation -> {
                OrderModel order = invocation.getArgument(0);
                ReflectionTestUtils.setField(order, "id", 999L);
                return order;
            });
            given(orderItemRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

            // when
            OrderResult result = orderService.create(userId, rawLines, 0L, null);

            // then
            assertAll(
                () -> assertThat(result.order().getUserId()).isEqualTo(userId),
                () -> assertThat(result.order().getTotalAmount()).isEqualTo(100_000L * 2 + 50_000L * 3),
                () -> assertThat(result.order().getStatus()).isEqualTo(OrderStatus.CREATED),
                () -> assertThat(result.items()).hasSize(2)
            );
            verify(orderRepository).save(any(OrderModel.class));
            verify(orderItemRepository).saveAll(anyList());
        }

        @DisplayName("할인액과 사용 쿠폰이 주어지면, 주문에 금액 3종과 usedCouponId 가 스냅샷된다.")
        @Test
        void snapshotsAmountsAndUsedCoupon_whenDiscountIsApplied() {
            // given
            Long userId = 1L;
            Long usedCouponId = 77L;
            List<OrderLine> rawLines = List.of(
                new OrderLine(100L, 2, "에어맥스 270", 100_000L, "나이키")
            );
            given(orderRepository.save(any(OrderModel.class))).willAnswer(invocation -> {
                OrderModel order = invocation.getArgument(0);
                ReflectionTestUtils.setField(order, "id", 999L);
                return order;
            });
            given(orderItemRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

            // when
            OrderResult result = orderService.create(userId, rawLines, 30_000L, usedCouponId);

            // then
            assertAll(
                () -> assertThat(result.order().getTotalAmount()).isEqualTo(200_000L),
                () -> assertThat(result.order().getDiscountAmount()).isEqualTo(30_000L),
                () -> assertThat(result.order().getFinalAmount()).isEqualTo(170_000L),
                () -> assertThat(result.order().getUsedCouponId()).isEqualTo(77L)
            );
        }

        @DisplayName("같은 productId 가 두 번 들어오면, OrderItem 1행으로 합산되어 저장된다.")
        @Test
        void mergesDuplicateProductLines_whenSameProductAppearsTwice() {
            // given
            Long userId = 1L;
            List<OrderLine> rawLines = List.of(
                new OrderLine(100L, 2, "에어맥스 270", 100_000L, "나이키"),
                new OrderLine(100L, 3, "에어맥스 270", 100_000L, "나이키")
            );
            given(orderRepository.save(any(OrderModel.class))).willAnswer(invocation -> {
                OrderModel order = invocation.getArgument(0);
                ReflectionTestUtils.setField(order, "id", 999L);
                return order;
            });
            given(orderItemRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

            // when
            OrderResult result = orderService.create(userId, rawLines, 0L, null);

            // then
            assertAll(
                () -> assertThat(result.items()).hasSize(1),
                () -> assertThat(result.items().get(0).getQuantity()).isEqualTo(5),
                () -> assertThat(result.order().getTotalAmount()).isEqualTo(100_000L * 5L)
            );
        }

        @DisplayName("lines 가 빈 리스트이면, EMPTY_ORDER_ITEMS 예외가 발생하고 저장이 수행되지 않는다.")
        @Test
        void throwsEmptyOrderItems_whenRawLinesIsEmpty() {
            // given
            Long userId = 1L;
            List<OrderLine> rawLines = List.of();

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> orderService.create(userId, rawLines, 0L, null));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.EMPTY_ORDER_ITEMS);
            verify(orderRepository, never()).save(any(OrderModel.class));
            verify(orderItemRepository, never()).saveAll(anyList());
        }

        @DisplayName("userId 가 null 이면, BAD_REQUEST 예외가 발생하고 저장이 수행되지 않는다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            // given
            Long userId = null;
            List<OrderLine> rawLines = List.of(
                new OrderLine(100L, 1, "에어맥스 270", 100_000L, "나이키")
            );

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> orderService.create(userId, rawLines, 0L, null));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(orderRepository, never()).save(any(OrderModel.class));
            verify(orderItemRepository, never()).saveAll(anyList());
        }
    }

    @DisplayName("본인 주문 목록을 조회할 때, ")
    @Nested
    class FindMine {

        @DisplayName("기간 내 주문을 저장소 정렬 그대로 각자의 items 와 묶어 반환한다.")
        @Test
        void returnsOrdersWithGroupedItems() {
            // given
            Long userId = 1L;
            OrderModel orderTwo = orderOf(2L, userId);
            OrderModel orderOne = orderOf(1L, userId);
            given(orderRepository.findByUserIdAndCreatedAtBetween(eq(userId), any(), any()))
                .willReturn(List.of(orderTwo, orderOne));
            given(orderItemRepository.findByOrderIdIn(anyCollection())).willReturn(List.of(
                OrderItem.of(2L, 100L, 1, "에어맥스", 100_000L, "나이키"),
                OrderItem.of(1L, 200L, 2, "스탠스미스", 50_000L, "아디다스")
            ));

            // when
            List<OrderResult> results = orderService.findMine(
                userId, OrderPeriod.of(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)));

            // then
            assertAll(
                () -> assertThat(results).hasSize(2),
                () -> assertThat(results.get(0).order().getId()).isEqualTo(2L),
                () -> assertThat(results.get(0).items()).hasSize(1),
                () -> assertThat(results.get(1).order().getId()).isEqualTo(1L),
                () -> assertThat(results.get(1).items()).hasSize(1)
            );
        }

        @DisplayName("기간 내 주문이 없으면 빈 목록을 반환하고 items 묶음 조회를 하지 않는다.")
        @Test
        void returnsEmptyListWithoutItemLookup_whenNoOrders() {
            // given
            Long userId = 1L;
            given(orderRepository.findByUserIdAndCreatedAtBetween(eq(userId), any(), any()))
                .willReturn(List.of());

            // when
            List<OrderResult> results = orderService.findMine(
                userId, OrderPeriod.of(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)));

            // then
            assertThat(results).isEmpty();
            verify(orderItemRepository, never()).findByOrderIdIn(anyCollection());
        }
    }

    @DisplayName("본인 주문 단건을 조회할 때, ")
    @Nested
    class FindOneOwnedBy {

        @DisplayName("본인 소유의 주문이면, items 와 함께 반환한다.")
        @Test
        void returnsOrderWithItems_whenOwnedByUser() {
            // given
            Long userId = 1L;
            Long orderId = 10L;
            given(orderRepository.findById(orderId)).willReturn(Optional.of(orderOf(orderId, userId)));
            given(orderItemRepository.findByOrderId(orderId)).willReturn(List.of(
                OrderItem.of(orderId, 100L, 1, "에어맥스", 100_000L, "나이키")
            ));

            // when
            OrderResult result = orderService.findOneOwnedBy(userId, orderId);

            // then
            assertAll(
                () -> assertThat(result.order().getId()).isEqualTo(orderId),
                () -> assertThat(result.items()).hasSize(1)
            );
        }

        @DisplayName("주문이 존재하지 않으면, ORDER_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsOrderNotFound_whenOrderDoesNotExist() {
            // given
            Long userId = 1L;
            Long orderId = 10L;
            given(orderRepository.findById(orderId)).willReturn(Optional.empty());

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> orderService.findOneOwnedBy(userId, orderId));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.ORDER_NOT_FOUND);
        }

        @DisplayName("타인 소유의 주문이면, ORDER_NOT_FOUND 예외가 발생한다 (존재 자체 숨김).")
        @Test
        void throwsOrderNotFound_whenOwnedByAnotherUser() {
            // given
            Long userId = 1L;
            Long ownerId = 2L;
            Long orderId = 10L;
            given(orderRepository.findById(orderId)).willReturn(Optional.of(orderOf(orderId, ownerId)));

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> orderService.findOneOwnedBy(userId, orderId));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.ORDER_NOT_FOUND);
        }
    }

    @DisplayName("어드민이 전체 주문을 조회할 때, ")
    @Nested
    class FindAll {

        @DisplayName("페이지 내용을 각자의 items 와 묶어 Page 로 반환한다.")
        @Test
        void returnsPageWithGroupedItems() {
            // given
            OrderModel orderTwo = orderOf(2L, 1L);
            OrderModel orderOne = orderOf(1L, 2L);
            Page<OrderModel> page = new PageImpl<>(
                List.of(orderTwo, orderOne), PageRequest.of(0, 20), 2);
            given(orderRepository.findAll(any(PageRequest.class))).willReturn(page);
            given(orderItemRepository.findByOrderIdIn(anyCollection())).willReturn(List.of(
                OrderItem.of(2L, 100L, 1, "에어맥스", 100_000L, "나이키"),
                OrderItem.of(1L, 200L, 2, "스탠스미스", 50_000L, "아디다스")
            ));

            // when
            Page<OrderResult> results = orderService.findAll(0, 20);

            // then
            assertAll(
                () -> assertThat(results.getTotalElements()).isEqualTo(2),
                () -> assertThat(results.getContent()).hasSize(2),
                () -> assertThat(results.getContent().get(0).order().getId()).isEqualTo(2L),
                () -> assertThat(results.getContent().get(0).items()).hasSize(1)
            );
        }
    }

    @DisplayName("어드민이 주문 단건을 조회할 때, ")
    @Nested
    class FindOne {

        @DisplayName("주문이 존재하면, 소유자와 무관하게 items 와 함께 반환한다.")
        @Test
        void returnsOrderWithItems_whenExists() {
            // given
            Long orderId = 10L;
            given(orderRepository.findById(orderId)).willReturn(Optional.of(orderOf(orderId, 999L)));
            given(orderItemRepository.findByOrderId(orderId)).willReturn(List.of(
                OrderItem.of(orderId, 100L, 1, "에어맥스", 100_000L, "나이키")
            ));

            // when
            OrderResult result = orderService.findOne(orderId);

            // then
            assertAll(
                () -> assertThat(result.order().getId()).isEqualTo(orderId),
                () -> assertThat(result.items()).hasSize(1)
            );
        }

        @DisplayName("주문이 존재하지 않으면, ORDER_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsOrderNotFound_whenOrderDoesNotExist() {
            // given
            Long orderId = 10L;
            given(orderRepository.findById(orderId)).willReturn(Optional.empty());

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> orderService.findOne(orderId));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.ORDER_NOT_FOUND);
        }
    }

    @DisplayName("결제를 시작할 때, ")
    @Nested
    class StartPayment {

        @DisplayName("본인 소유의 결제 가능한 주문이면, PAYMENT_PENDING 으로 전이되고 저장된다.")
        @Test
        void transitionsToPaymentPending_whenOwnedAndPayable() {
            // given
            Long userId = 1L;
            Long orderId = 10L;
            given(orderRepository.findById(orderId)).willReturn(Optional.of(orderOf(orderId, userId)));
            given(orderRepository.save(any(OrderModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            OrderModel result = orderService.startPayment(userId, orderId);

            // then
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
            verify(orderRepository).save(any(OrderModel.class));
        }

        @DisplayName("주문이 존재하지 않으면, ORDER_NOT_FOUND 예외가 발생하고 저장하지 않는다.")
        @Test
        void throwsOrderNotFound_whenMissing() {
            // given
            Long userId = 1L;
            Long orderId = 10L;
            given(orderRepository.findById(orderId)).willReturn(Optional.empty());

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> orderService.startPayment(userId, orderId));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.ORDER_NOT_FOUND);
            verify(orderRepository, never()).save(any(OrderModel.class));
        }

        @DisplayName("타인 소유의 주문이면, ORDER_NOT_FOUND 예외가 발생한다 (존재 자체 숨김).")
        @Test
        void throwsOrderNotFound_whenOwnedByAnother() {
            // given
            Long userId = 1L;
            Long ownerId = 2L;
            Long orderId = 10L;
            given(orderRepository.findById(orderId)).willReturn(Optional.of(orderOf(orderId, ownerId)));

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> orderService.startPayment(userId, orderId));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.ORDER_NOT_FOUND);
            verify(orderRepository, never()).save(any(OrderModel.class));
        }
    }

    @DisplayName("결제 완료를 반영할 때, ")
    @Nested
    class MarkPaid {

        @DisplayName("결제대기 주문이면, PAID 로 전이되고 저장된다.")
        @Test
        void transitionsToPaid_whenPaymentPending() {
            // given
            Long orderId = 10L;
            OrderModel order = orderOf(orderId, 1L);
            order.startPayment();
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
            given(orderRepository.save(any(OrderModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            orderService.markPaid(orderId);

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            verify(orderRepository).save(order);
        }

        @DisplayName("주문이 존재하지 않으면, ORDER_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsOrderNotFound_whenMissing() {
            // given
            Long orderId = 10L;
            given(orderRepository.findById(orderId)).willReturn(Optional.empty());

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> orderService.markPaid(orderId));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.ORDER_NOT_FOUND);
        }
    }

    @DisplayName("결제 실패를 반영할 때, ")
    @Nested
    class MarkPaymentFailed {

        @DisplayName("결제대기 주문이면, PAYMENT_FAILED 로 전이되고 저장된다.")
        @Test
        void transitionsToPaymentFailed_whenPaymentPending() {
            // given
            Long orderId = 10L;
            OrderModel order = orderOf(orderId, 1L);
            order.startPayment();
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
            given(orderRepository.save(any(OrderModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            orderService.markPaymentFailed(orderId);

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
            verify(orderRepository).save(order);
        }
    }

    private OrderModel orderOf(Long id, Long userId) {
        OrderModel order = OrderModel.create(userId, OrderLines.of(List.of(
            new OrderLine(100L, 1, "에어맥스", 100_000L, "나이키")
        )));
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
