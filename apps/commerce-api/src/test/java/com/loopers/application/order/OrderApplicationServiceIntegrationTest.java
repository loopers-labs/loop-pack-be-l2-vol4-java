package com.loopers.application.order;

import com.loopers.domain.common.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderApplicationServiceIntegrationTest {

    private static final Long USER_A = 10L;
    private static final Long USER_B = 20L;

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long brandId;

    @BeforeEach
    void setUp() {
        brandId = brandJpaRepository.save(Brand.create("브랜드A", "소개")).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long saveProduct(String name, long price, int stock) {
        return productJpaRepository.save(Product.create(brandId, name, Money.of(price), Stock.of(stock))).getId();
    }

    @DisplayName("place 는 ")
    @Nested
    class Place {

        @DisplayName("정상 주문 시 재고를 차감하고 주문을 CREATED 상태로 저장한다. (AC-07-2)")
        @Test
        void decreasesStockAndPersistsOrder_whenValid() {
            Long p1 = saveProduct("상품1", 1_000L, 10);
            Long p2 = saveProduct("상품2", 2_000L, 5);

            OrderInfo.Created result = orderApplicationService.place(
                    new OrderCriteria.Place(USER_A, List.of(
                            new OrderCriteria.Line(p1, 2),
                            new OrderCriteria.Line(p2, 1)
                    )));

            assertThat(result.id()).isNotNull();
            assertThat(result.userId()).isEqualTo(USER_A);
            assertThat(result.totalAmount()).isEqualTo(4_000L);
            assertThat(result.status()).isEqualTo(OrderStatus.CREATED);
            assertThat(result.items()).hasSize(2);

            assertThat(productJpaRepository.findById(p1).orElseThrow().getStock().getQuantity()).isEqualTo(8);
            assertThat(productJpaRepository.findById(p2).orElseThrow().getStock().getQuantity()).isEqualTo(4);
        }

        @DisplayName("존재하지 않는 상품이 포함되면 NOT_FOUND 를 던지고 재고는 변하지 않는다. (AC-07-1)")
        @Test
        void throwsNotFound_andDoesNotMutate_whenProductMissing() {
            Long p1 = saveProduct("상품1", 1_000L, 10);

            CoreException result = assertThrows(CoreException.class,
                    () -> orderApplicationService.place(new OrderCriteria.Place(USER_A, List.of(
                            new OrderCriteria.Line(p1, 1),
                            new OrderCriteria.Line(99999L, 1)
                    ))));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            assertThat(productJpaRepository.findById(p1).orElseThrow().getStock().getQuantity()).isEqualTo(10);
        }

        @DisplayName("재고가 하나라도 부족하면 BAD_REQUEST 를 던지고 모든 재고가 보존된다. (AC-07-3, AC-07-4)")
        @Test
        void throwsBadRequest_andRollsBack_whenStockShortage() {
            Long p1 = saveProduct("상품1", 1_000L, 10);
            Long p2 = saveProduct("상품2", 2_000L, 1);

            CoreException result = assertThrows(CoreException.class,
                    () -> orderApplicationService.place(new OrderCriteria.Place(USER_A, List.of(
                            new OrderCriteria.Line(p1, 2),
                            new OrderCriteria.Line(p2, 5)
                    ))));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(productJpaRepository.findById(p1).orElseThrow().getStock().getQuantity()).isEqualTo(10);
            assertThat(productJpaRepository.findById(p2).orElseThrow().getStock().getQuantity()).isEqualTo(1);
        }

        @DisplayName("주문 항목에는 주문 시점의 상품명·단가가 스냅샷으로 저장된다. (AC-07-5)")
        @Test
        void snapshotsProductNameAndPrice_atOrderTime() {
            Long p1 = saveProduct("상품1", 1_000L, 10);

            OrderInfo.Created result = orderApplicationService.place(
                    new OrderCriteria.Place(USER_A, List.of(new OrderCriteria.Line(p1, 2))));

            OrderInfo.Item item = result.items().get(0);
            assertThat(item.productId()).isEqualTo(p1);
            assertThat(item.productName()).isEqualTo("상품1");
            assertThat(item.unitPrice()).isEqualTo(1_000L);
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.subtotal()).isEqualTo(2_000L);
        }
    }

    @DisplayName("getMyOrder 는 ")
    @Nested
    class GetMyOrder {

        @DisplayName("본인 주문이면 Detail 을 돌려준다. (AC-09-1)")
        @Test
        void returnsDetail_whenOwner() {
            Long p1 = saveProduct("상품1", 1_000L, 10);
            OrderInfo.Created created = orderApplicationService.place(
                    new OrderCriteria.Place(USER_A, List.of(new OrderCriteria.Line(p1, 2))));

            OrderInfo.Detail result = orderApplicationService.getMyOrder(USER_A, created.id());

            assertThat(result.id()).isEqualTo(created.id());
            assertThat(result.userId()).isEqualTo(USER_A);
            assertThat(result.items()).hasSize(1);
            assertThat(result.totalAmount()).isEqualTo(2_000L);
        }

        @DisplayName("다른 사용자의 주문을 요청하면 FORBIDDEN. (AC-09-2)")
        @Test
        void throwsForbidden_whenNotOwner() {
            Long p1 = saveProduct("상품1", 1_000L, 10);
            OrderInfo.Created created = orderApplicationService.place(
                    new OrderCriteria.Place(USER_A, List.of(new OrderCriteria.Line(p1, 1))));

            CoreException result = assertThrows(CoreException.class,
                    () -> orderApplicationService.getMyOrder(USER_B, created.id()));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }

        @DisplayName("존재하지 않는 주문이면 NOT_FOUND.")
        @Test
        void throwsNotFound_whenMissing() {
            CoreException result = assertThrows(CoreException.class,
                    () -> orderApplicationService.getMyOrder(USER_A, 99999L));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("getMyOrders 는 ")
    @Nested
    class GetMyOrders {

        @DisplayName("본인 주문만 최신순으로 돌려주고 타 사용자 주문은 제외한다. (AC-08-1)")
        @Test
        void returnsOnlyOwnersOrders_sortedByLatest() {
            Long p1 = saveProduct("상품1", 1_000L, 10);
            Long p2 = saveProduct("상품2", 1_000L, 10);
            Long p3 = saveProduct("상품3", 1_000L, 10);

            OrderInfo.Created o1 = orderApplicationService.place(
                    new OrderCriteria.Place(USER_A, List.of(new OrderCriteria.Line(p1, 1))));
            OrderInfo.Created o2 = orderApplicationService.place(
                    new OrderCriteria.Place(USER_A, List.of(new OrderCriteria.Line(p2, 1))));
            orderApplicationService.place(
                    new OrderCriteria.Place(USER_B, List.of(new OrderCriteria.Line(p3, 1))));

            PageResult<OrderInfo.ListItem> result = orderApplicationService.getMyOrders(
                    new OrderCriteria.MySearch(USER_A, null, null, 0, 20));

            assertThat(result.content()).hasSize(2);
            assertThat(result.content()).allMatch(item -> item.userId().equals(USER_A));
            assertThat(result.content()).extracting(OrderInfo.ListItem::id)
                    .containsExactly(o2.id(), o1.id());
            assertThat(result.totalElements()).isEqualTo(2L);
        }

        @DisplayName("페이지 사이즈에 맞춰 잘리고 hasNext 를 정확히 노출한다.")
        @Test
        void paginates() {
            Long p1 = saveProduct("상품1", 1_000L, 10);
            for (int i = 0; i < 3; i++) {
                orderApplicationService.place(
                        new OrderCriteria.Place(USER_A, List.of(new OrderCriteria.Line(p1, 1))));
            }

            PageResult<OrderInfo.ListItem> page0 = orderApplicationService.getMyOrders(
                    new OrderCriteria.MySearch(USER_A, null, null, 0, 2));
            PageResult<OrderInfo.ListItem> page1 = orderApplicationService.getMyOrders(
                    new OrderCriteria.MySearch(USER_A, null, null, 1, 2));

            assertThat(page0.content()).hasSize(2);
            assertThat(page0.hasNext()).isTrue();
            assertThat(page1.content()).hasSize(1);
            assertThat(page1.hasNext()).isFalse();
            assertThat(page0.totalElements()).isEqualTo(3L);
        }

        @DisplayName("기간 범위 밖이면 빈 목록을 돌려준다.")
        @Test
        void excludesOrders_outsideDateRange() {
            Long p1 = saveProduct("상품1", 1_000L, 10);
            orderApplicationService.place(
                    new OrderCriteria.Place(USER_A, List.of(new OrderCriteria.Line(p1, 1))));

            LocalDate yesterday = LocalDate.now().minusDays(1);
            PageResult<OrderInfo.ListItem> result = orderApplicationService.getMyOrders(
                    new OrderCriteria.MySearch(USER_A, null, yesterday, 0, 20));

            assertThat(result.content()).isEmpty();
        }
    }

    @DisplayName("getOrder (어드민) 은 ")
    @Nested
    class GetOrder {

        @DisplayName("존재하는 주문을 누가 주문했든 Detail 로 돌려준다. (AC-18-2)")
        @Test
        void returnsDetail_regardlessOfOwner() {
            Long p1 = saveProduct("상품1", 1_000L, 10);
            OrderInfo.Created created = orderApplicationService.place(
                    new OrderCriteria.Place(USER_A, List.of(new OrderCriteria.Line(p1, 2))));

            OrderInfo.Detail result = orderApplicationService.getOrder(created.id());

            assertThat(result.id()).isEqualTo(created.id());
            assertThat(result.userId()).isEqualTo(USER_A);
            assertThat(result.totalAmount()).isEqualTo(2_000L);
        }

        @DisplayName("존재하지 않는 주문이면 NOT_FOUND.")
        @Test
        void throwsNotFound_whenMissing() {
            CoreException result = assertThrows(CoreException.class,
                    () -> orderApplicationService.getOrder(99999L));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("getAllOrders (어드민) 은 모든 사용자의 주문을 페이징해서 돌려준다. (AC-18-1)")
    @Test
    void getAllOrders_returnsAllUsersOrders() {
        Long p1 = saveProduct("상품1", 1_000L, 10);
        Long p2 = saveProduct("상품2", 1_000L, 10);
        orderApplicationService.place(
                new OrderCriteria.Place(USER_A, List.of(new OrderCriteria.Line(p1, 1))));
        orderApplicationService.place(
                new OrderCriteria.Place(USER_B, List.of(new OrderCriteria.Line(p2, 1))));

        PageResult<OrderInfo.ListItem> result = orderApplicationService.getAllOrders(
                new OrderCriteria.AdminSearch(0, 20));

        assertThat(result.content()).hasSize(2);
        assertThat(result.content()).extracting(OrderInfo.ListItem::userId)
                .containsExactlyInAnyOrder(USER_A, USER_B);
    }
}
