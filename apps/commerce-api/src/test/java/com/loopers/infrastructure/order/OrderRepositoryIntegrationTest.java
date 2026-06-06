package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderItemRepository;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderLines;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrderRepositoryIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public OrderRepositoryIntegrationTest(
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("findByUserIdAndCreatedAtBetween 는, ")
    @Nested
    class FindByUserIdAndCreatedAtBetween {

        @DisplayName("기간 내 본인 주문을 createdAt desc, id desc 로 반환한다.")
        @Test
        void returnsOwnOrdersInLatestOrder_whenWithinPeriod() {
            // given
            Long userId = 1L;
            Long firstOrderId = saveOrder(userId);
            Long secondOrderId = saveOrder(userId);
            Long thirdOrderId = saveOrder(userId);

            // when
            List<OrderModel> found = orderRepository.findByUserIdAndCreatedAtBetween(
                userId, todayStart(), tomorrowStart());

            // then
            assertThat(found).extracting(OrderModel::getId)
                .containsExactly(thirdOrderId, secondOrderId, firstOrderId);
        }

        @DisplayName("다른 회원의 주문은 결과에 포함되지 않는다.")
        @Test
        void excludesOtherUsersOrders() {
            // given
            Long userId = 1L;
            Long otherUserId = 2L;
            Long myOrderId = saveOrder(userId);
            saveOrder(otherUserId);

            // when
            List<OrderModel> found = orderRepository.findByUserIdAndCreatedAtBetween(
                userId, todayStart(), tomorrowStart());

            // then
            assertAll(
                () -> assertThat(found).hasSize(1),
                () -> assertThat(found.get(0).getId()).isEqualTo(myOrderId)
            );
        }

        @DisplayName("조회 기간 바깥에 생성된 주문은 제외된다.")
        @Test
        void excludesOrdersOutsidePeriod() {
            // given
            Long userId = 1L;
            saveOrder(userId);
            ZonedDateTime pastFrom = LocalDate.now(KST).minusDays(10).atStartOfDay(KST);
            ZonedDateTime pastTo = LocalDate.now(KST).minusDays(9).atStartOfDay(KST);

            // when
            List<OrderModel> found = orderRepository.findByUserIdAndCreatedAtBetween(
                userId, pastFrom, pastTo);

            // then
            assertThat(found).isEmpty();
        }

        @DisplayName("기간 내 주문이 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoOrders() {
            // given
            Long userId = 1L;

            // when
            List<OrderModel> found = orderRepository.findByUserIdAndCreatedAtBetween(
                userId, todayStart(), tomorrowStart());

            // then
            assertThat(found).isEmpty();
        }
    }

    @DisplayName("findByOrderIdIn 는, ")
    @Nested
    class FindByOrderIdIn {

        @DisplayName("여러 주문의 항목을 한 번에 묶어 반환한다.")
        @Test
        void returnsItemsForMultipleOrders() {
            // given
            Long firstOrderId = saveOrder(1L);
            Long secondOrderId = saveOrder(1L);

            // when
            List<OrderItem> found = orderItemRepository.findByOrderIdIn(
                List.of(firstOrderId, secondOrderId));

            // then
            assertAll(
                () -> assertThat(found).hasSize(2),
                () -> assertThat(found).extracting(OrderItem::getOrderId)
                    .containsExactlyInAnyOrder(firstOrderId, secondOrderId)
            );
        }

        @DisplayName("빈 컬렉션으로 조회하면 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenOrderIdsEmpty() {
            // given
            saveOrder(1L);

            // when
            List<OrderItem> found = orderItemRepository.findByOrderIdIn(List.of());

            // then
            assertThat(found).isEmpty();
        }
    }

    @DisplayName("findAll(Pageable) 은, ")
    @Nested
    class FindAll {

        @DisplayName("전체 주문을 페이지 단위로 반환한다.")
        @Test
        void returnsOrdersByPage() {
            // given
            saveOrder(1L);
            saveOrder(2L);
            saveOrder(3L);

            // when
            var firstPage = orderRepository.findAll(PageRequest.of(0, 2));

            // then
            assertAll(
                () -> assertThat(firstPage.getTotalElements()).isEqualTo(3),
                () -> assertThat(firstPage.getContent()).hasSize(2),
                () -> assertThat(firstPage.getTotalPages()).isEqualTo(2)
            );
        }
    }

    private Long saveOrder(Long userId) {
        OrderLines lines = OrderLines.of(List.of(
            new OrderLine(100L, 1, "에어맥스 270", 100_000L, "나이키")
        ));
        OrderModel order = orderRepository.save(OrderModel.create(userId, lines));
        orderItemRepository.saveAll(List.of(
            OrderItem.of(order.getId(), 100L, 1, "에어맥스 270", 100_000L, "나이키")
        ));
        return order.getId();
    }

    private ZonedDateTime todayStart() {
        return LocalDate.now(KST).atStartOfDay(KST);
    }

    private ZonedDateTime tomorrowStart() {
        return LocalDate.now(KST).plusDays(1).atStartOfDay(KST);
    }
}
