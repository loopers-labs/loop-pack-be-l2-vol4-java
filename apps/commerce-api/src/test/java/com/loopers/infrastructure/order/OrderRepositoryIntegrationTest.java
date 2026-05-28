package com.loopers.infrastructure.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class OrderRepositoryIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private OrderItemJpaRepository orderItemJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderItemModel orderItem(Long productId, int unitPrice, int quantity) {
        return OrderItemModel.builder()
            .productId(productId)
            .productName("감성 가디건")
            .productBrandName("감성 브랜드")
            .unitPrice(unitPrice)
            .rawQuantity(quantity)
            .build();
    }

    private OrderModel orderOf(Long userId, List<OrderItemModel> orderItems) {
        int totalPrice = orderItems.stream()
            .mapToInt(OrderItemModel::totalPrice)
            .sum();

        return OrderModel.builder()
            .userId(userId)
            .orderedAt(ZonedDateTime.now())
            .totalPrice(totalPrice)
            .build();
    }

    private OrderModel orderOf(Long userId, ZonedDateTime orderedAt, List<OrderItemModel> orderItems) {
        int totalPrice = orderItems.stream()
            .mapToInt(OrderItemModel::totalPrice)
            .sum();

        return OrderModel.builder()
            .userId(userId)
            .orderedAt(orderedAt)
            .totalPrice(totalPrice)
            .build();
    }

    private OrderModel saveOrder(Long userId) {
        List<OrderItemModel> orderItems = new ArrayList<>(List.of(orderItem(10L, 10_000, 1)));

        return orderRepository.save(orderOf(userId, orderItems), orderItems);
    }

    private OrderModel saveOrderAt(Long userId, ZonedDateTime orderedAt) {
        List<OrderItemModel> orderItems = new ArrayList<>(List.of(orderItem(10L, 10_000, 1)));

        return orderRepository.save(orderOf(userId, orderedAt, orderItems), orderItems);
    }

    private ZonedDateTime startOfToday() {
        return LocalDate.now().atStartOfDay(ZoneId.systemDefault());
    }

    private ZonedDateTime startOfTomorrow() {
        return LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault());
    }

    @DisplayName("주문을 저장할 때,")
    @Nested
    class Save {

        @DisplayName("주문과 주문 항목이 함께 저장되고 항목에 주문 식별자가 배정된다.")
        @Test
        void persistsOrderAndItems_withAssignedOrderId() {
            // arrange
            List<OrderItemModel> orderItems = new ArrayList<>(List.of(orderItem(10L, 10_000, 2), orderItem(20L, 5_000, 3)));
            OrderModel order = orderOf(1L, orderItems);

            // act
            OrderModel savedOrder = orderRepository.save(order, orderItems);

            // assert
            OrderModel reloadedOrder = orderJpaRepository.findById(savedOrder.getId()).orElseThrow();
            List<OrderItemModel> reloadedItems = orderItemJpaRepository.findByOrderIdAndDeletedAtIsNull(savedOrder.getId());
            assertAll(
                () -> assertThat(savedOrder.getId()).isNotNull(),
                () -> assertThat(reloadedOrder.getUserId()).isEqualTo(1L),
                () -> assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.CREATED),
                () -> assertThat(reloadedOrder.getTotalPrice()).isEqualTo(35_000),
                () -> assertThat(reloadedItems).hasSize(2),
                () -> assertThat(reloadedItems).allSatisfy(item -> assertThat(item.getOrderId()).isEqualTo(savedOrder.getId()))
            );
        }
    }

    @DisplayName("활성 주문을 식별자로 조회할 때,")
    @Nested
    class GetActiveById {

        @DisplayName("삭제되지 않은 주문이면 해당 주문을 반환한다.")
        @Test
        void returnsActiveOrder() {
            // arrange
            OrderModel savedOrder = saveOrder(1L);

            // act
            OrderModel foundOrder = orderRepository.getActiveById(savedOrder.getId());

            // assert
            assertThat(foundOrder.getId()).isEqualTo(savedOrder.getId());
        }

        @DisplayName("이미 삭제된 주문이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenDeleted() {
            // arrange
            OrderModel savedOrder = saveOrder(1L);
            savedOrder.delete();
            orderJpaRepository.saveAndFlush(savedOrder);

            // act & assert
            assertThatThrownBy(() -> orderRepository.getActiveById(savedOrder.getId()))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 식별자면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenAbsent() {
            // act & assert
            assertThatThrownBy(() -> orderRepository.getActiveById(99999L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("본인 활성 주문을 식별자로 조회할 때,")
    @Nested
    class GetActiveByIdAndUserId {

        @DisplayName("본인 주문이면 해당 주문을 반환한다.")
        @Test
        void returnsOrder_whenOwned() {
            // arrange
            OrderModel savedOrder = saveOrder(1L);

            // act
            OrderModel foundOrder = orderRepository.getActiveByIdAndUserId(savedOrder.getId(), 1L);

            // assert
            assertThat(foundOrder.getId()).isEqualTo(savedOrder.getId());
        }

        @DisplayName("타인의 주문이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOwnedByOther() {
            // arrange
            OrderModel savedOrder = saveOrder(1L);

            // act & assert
            assertThatThrownBy(() -> orderRepository.getActiveByIdAndUserId(savedOrder.getId(), 2L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 주문이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenAbsent() {
            // act & assert
            assertThatThrownBy(() -> orderRepository.getActiveByIdAndUserId(99999L, 1L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("본인 활성 주문을 날짜 범위로 조회할 때,")
    @Nested
    class FindActiveByUserIdAndOrderedAtBetween {

        @DisplayName("범위 안의 본인 주문만 반환하고 타 회원 주문은 제외한다.")
        @Test
        void returnsOwnOrdersInRange() {
            // arrange
            saveOrder(1L);
            saveOrder(1L);
            saveOrder(2L);

            // act
            Page<OrderModel> orders = orderRepository.findActiveByUserIdAndOrderedAtBetween(1L, startOfToday(), startOfTomorrow(), 0, 10);

            // assert
            assertAll(
                () -> assertThat(orders.getTotalElements()).isEqualTo(2),
                () -> assertThat(orders.getContent()).allSatisfy(order -> assertThat(order.getUserId()).isEqualTo(1L))
            );
        }

        @DisplayName("범위를 벗어난 주문은 제외한다.")
        @Test
        void excludesOrdersOutOfRange() {
            // arrange
            saveOrder(1L);
            ZonedDateTime pastStart = LocalDate.now().minusDays(10).atStartOfDay(ZoneId.systemDefault());
            ZonedDateTime pastEnd = LocalDate.now().minusDays(5).atStartOfDay(ZoneId.systemDefault());

            // act
            Page<OrderModel> orders = orderRepository.findActiveByUserIdAndOrderedAtBetween(1L, pastStart, pastEnd, 0, 10);

            // assert
            assertThat(orders.getTotalElements()).isEqualTo(0);
        }

        @DisplayName("이미 삭제된 주문은 제외한다.")
        @Test
        void excludesDeletedOrder() {
            // arrange
            saveOrder(1L);
            OrderModel deletedOrder = saveOrder(1L);
            deletedOrder.delete();
            orderJpaRepository.saveAndFlush(deletedOrder);

            // act
            Page<OrderModel> orders = orderRepository.findActiveByUserIdAndOrderedAtBetween(1L, startOfToday(), startOfTomorrow(), 0, 10);

            // assert
            assertThat(orders.getTotalElements()).isEqualTo(1);
        }

        @DisplayName("페이지 크기와 오프셋대로 페이징하고 총 개수를 보존한다.")
        @Test
        void appliesPaging() {
            // arrange
            saveOrder(1L);
            saveOrder(1L);
            saveOrder(1L);

            // act
            Page<OrderModel> firstPage = orderRepository.findActiveByUserIdAndOrderedAtBetween(1L, startOfToday(), startOfTomorrow(), 0, 2);

            // assert
            assertAll(
                () -> assertThat(firstPage.getTotalElements()).isEqualTo(3),
                () -> assertThat(firstPage.getTotalPages()).isEqualTo(2),
                () -> assertThat(firstPage.getContent()).hasSize(2)
            );
        }

        @DisplayName("2건 이상의 주문이 있으면 주문 시각 내림차순으로 반환된다.")
        @Test
        void returnsSortedByOrderedAtDesc() {
            // arrange
            ZonedDateTime earlier = startOfToday().plusHours(1);
            ZonedDateTime later = startOfToday().plusHours(3);
            OrderModel firstSaved = saveOrderAt(1L, earlier);
            OrderModel secondSaved = saveOrderAt(1L, later);

            // act
            Page<OrderModel> orders = orderRepository.findActiveByUserIdAndOrderedAtBetween(1L, startOfToday(), startOfTomorrow(), 0, 10);

            // assert
            assertThat(orders.getContent())
                .extracting(OrderModel::getId)
                .containsExactly(secondSaved.getId(), firstSaved.getId());
        }

        @DisplayName("시작 경계 시각의 주문은 포함되고 종료 경계 시각의 주문은 제외된다.")
        @Test
        void includesStartBoundary_andExcludesEndBoundary() {
            // arrange
            ZonedDateTime rangeStart = startOfToday().plusHours(6);
            ZonedDateTime rangeEnd = startOfToday().plusHours(12);
            OrderModel atStart = saveOrderAt(1L, rangeStart);
            saveOrderAt(1L, rangeEnd);

            // act
            Page<OrderModel> orders = orderRepository.findActiveByUserIdAndOrderedAtBetween(1L, rangeStart, rangeEnd, 0, 10);

            // assert
            assertAll(
                () -> assertThat(orders.getTotalElements()).isEqualTo(1),
                () -> assertThat(orders.getContent().get(0).getId()).isEqualTo(atStart.getId())
            );
        }
    }

    @DisplayName("전체 활성 주문을 페이지로 조회할 때,")
    @Nested
    class FindActiveByPage {

        @DisplayName("회원 구분 없이 삭제되지 않은 전체 주문을 페이징하고 총 개수를 보존한다.")
        @Test
        void returnsAllActiveOrders() {
            // arrange
            saveOrder(1L);
            saveOrder(2L);
            OrderModel deletedOrder = saveOrder(3L);
            deletedOrder.delete();
            orderJpaRepository.saveAndFlush(deletedOrder);

            // act
            Page<OrderModel> firstPage = orderRepository.findActiveByPage(0, 10);

            // assert
            assertThat(firstPage.getTotalElements()).isEqualTo(2);
        }

        @DisplayName("2건 이상의 주문이 있으면 주문 시각 내림차순으로 반환된다.")
        @Test
        void returnsSortedByOrderedAtDesc() {
            // arrange
            ZonedDateTime earlier = ZonedDateTime.now().minusHours(2);
            ZonedDateTime later = ZonedDateTime.now();
            OrderModel firstSaved = saveOrderAt(1L, earlier);
            OrderModel secondSaved = saveOrderAt(2L, later);

            // act
            Page<OrderModel> page = orderRepository.findActiveByPage(0, 10);

            // assert
            assertThat(page.getContent())
                .extracting(OrderModel::getId)
                .containsExactly(secondSaved.getId(), firstSaved.getId());
        }

        @DisplayName("조회된 주문의 헤더 레벨 필드(userId·status·totalPrice)가 저장한 값과 일치한다.")
        @Test
        void returnsCorrectHeaderFields() {
            // arrange
            saveOrder(5L);

            // act
            Page<OrderModel> page = orderRepository.findActiveByPage(0, 10);
            OrderModel found = page.getContent().get(0);

            // assert
            assertAll(
                () -> assertThat(found.getUserId()).isEqualTo(5L),
                () -> assertThat(found.getStatus()).isEqualTo(OrderStatus.CREATED),
                () -> assertThat(found.getTotalPrice()).isEqualTo(10_000)
            );
        }
    }

    @DisplayName("주문 항목을 주문 식별자로 조회할 때,")
    @Nested
    class FindActiveItemsByOrderId {

        @DisplayName("해당 주문의 삭제되지 않은 항목을 반환한다.")
        @Test
        void returnsActiveItems() {
            // arrange
            List<OrderItemModel> orderItems = new ArrayList<>(List.of(orderItem(10L, 10_000, 2), orderItem(20L, 5_000, 3)));
            OrderModel savedOrder = orderRepository.save(orderOf(1L, orderItems), orderItems);

            // act
            List<OrderItemModel> foundItems = orderRepository.findActiveItemsByOrderId(savedOrder.getId());

            // assert
            assertThat(foundItems).hasSize(2);
        }

        @DisplayName("이미 삭제된 항목은 제외한다.")
        @Test
        void excludesDeletedItem() {
            // arrange
            List<OrderItemModel> orderItems = new ArrayList<>(List.of(orderItem(10L, 10_000, 2), orderItem(20L, 5_000, 3)));
            OrderModel savedOrder = orderRepository.save(orderOf(1L, orderItems), orderItems);
            OrderItemModel deletedItem = orderItemJpaRepository.findByOrderIdAndDeletedAtIsNull(savedOrder.getId()).get(0);
            deletedItem.delete();
            orderItemJpaRepository.saveAndFlush(deletedItem);

            // act
            List<OrderItemModel> foundItems = orderRepository.findActiveItemsByOrderId(savedOrder.getId());

            // assert
            assertThat(foundItems).hasSize(1);
        }
    }
}
