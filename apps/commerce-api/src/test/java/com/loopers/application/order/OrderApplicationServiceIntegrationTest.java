package com.loopers.application.order;

import com.loopers.domain.common.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.DiscountPolicy;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.inventory.InventoryJpaRepository;
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
import java.time.ZonedDateTime;
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
    private InventoryJpaRepository inventoryJpaRepository;

    @Autowired
    private CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

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
        Long productId = productJpaRepository.save(Product.create(brandId, name, Money.of(price))).getId();
        inventoryJpaRepository.save(Inventory.create(productId, stock));
        return productId;
    }

    private int stockOf(Long productId) {
        return inventoryJpaRepository.findByProductIdAndDeletedAtIsNull(productId).orElseThrow().getQuantity();
    }

    private Long issueCoupon(Long userId, DiscountType type, long value) {
        CouponTemplate template = couponTemplateJpaRepository.save(
                CouponTemplate.create("쿠폰", DiscountPolicy.of(type, value), 30));
        return userCouponJpaRepository.save(UserCoupon.issue(userId, template, ZonedDateTime.now())).getId();
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
                    new OrderCriteria.Place(USER_A, null, List.of(
                            new OrderCriteria.Line(p1, 2),
                            new OrderCriteria.Line(p2, 1)
                    )));

            assertThat(result.id()).isNotNull();
            assertThat(result.userId()).isEqualTo(USER_A);
            assertThat(result.totalAmount()).isEqualTo(4_000L);
            assertThat(result.status()).isEqualTo(OrderStatus.CREATED);
            assertThat(result.items()).hasSize(2);

            assertThat(stockOf(p1)).isEqualTo(8);
            assertThat(stockOf(p2)).isEqualTo(4);
        }

        @DisplayName("존재하지 않는 상품이 포함되면 NOT_FOUND 를 던지고 재고는 변하지 않는다. (AC-07-1)")
        @Test
        void throwsNotFound_andDoesNotMutate_whenProductMissing() {
            Long p1 = saveProduct("상품1", 1_000L, 10);

            CoreException result = assertThrows(CoreException.class,
                    () -> orderApplicationService.place(new OrderCriteria.Place(USER_A, null, List.of(
                            new OrderCriteria.Line(p1, 1),
                            new OrderCriteria.Line(99999L, 1)
                    ))));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            assertThat(stockOf(p1)).isEqualTo(10);
        }

        @DisplayName("재고가 하나라도 부족하면 BAD_REQUEST 를 던지고 모든 재고가 보존된다. (AC-07-3, AC-07-4)")
        @Test
        void throwsBadRequest_andRollsBack_whenStockShortage() {
            Long p1 = saveProduct("상품1", 1_000L, 10);
            Long p2 = saveProduct("상품2", 2_000L, 1);

            CoreException result = assertThrows(CoreException.class,
                    () -> orderApplicationService.place(new OrderCriteria.Place(USER_A, null, List.of(
                            new OrderCriteria.Line(p1, 2),
                            new OrderCriteria.Line(p2, 5)
                    ))));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(stockOf(p1)).isEqualTo(10);
            assertThat(stockOf(p2)).isEqualTo(1);
        }

        @DisplayName("주문 항목에는 주문 시점의 상품명·단가가 스냅샷으로 저장된다. (AC-07-5)")
        @Test
        void snapshotsProductNameAndPrice_atOrderTime() {
            Long p1 = saveProduct("상품1", 1_000L, 10);

            OrderInfo.Created result = orderApplicationService.place(
                    new OrderCriteria.Place(USER_A, null, List.of(new OrderCriteria.Line(p1, 2))));

            OrderInfo.Item item = result.items().get(0);
            assertThat(item.productId()).isEqualTo(p1);
            assertThat(item.productName()).isEqualTo("상품1");
            assertThat(item.unitPrice()).isEqualTo(1_000L);
            assertThat(item.quantity()).isEqualTo(2);
            assertThat(item.subtotal()).isEqualTo(2_000L);
        }
    }

    @DisplayName("place 에 쿠폰을 지정하면 ")
    @Nested
    class PlaceWithCoupon {

        @DisplayName("할인액·최종 금액이 이력으로 저장되고 쿠폰은 사용 완료(USED)가 된다. (AC-07-6, AC-07-8)")
        @Test
        void appliesDiscount_andMarksCouponUsed() {
            Long p1 = saveProduct("상품1", 1_000L, 10);
            Long couponId = issueCoupon(USER_A, DiscountType.FIXED, 500L);

            OrderInfo.Created result = orderApplicationService.place(
                    new OrderCriteria.Place(USER_A, couponId, List.of(new OrderCriteria.Line(p1, 2)))); // 2_000

            assertThat(result.originalAmount()).isEqualTo(2_000L);
            assertThat(result.discountAmount()).isEqualTo(500L);
            assertThat(result.totalAmount()).isEqualTo(1_500L);

            UserCoupon coupon = userCouponJpaRepository.findById(couponId).orElseThrow();
            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
            assertThat(coupon.getOrderId()).isEqualTo(result.id());
        }

        @DisplayName("존재하지 않는 쿠폰이면 NOT_FOUND 를 던지고 재고는 변하지 않는다.")
        @Test
        void throwsNotFound_whenCouponMissing() {
            Long p1 = saveProduct("상품1", 1_000L, 10);

            CoreException result = assertThrows(CoreException.class,
                    () -> orderApplicationService.place(
                            new OrderCriteria.Place(USER_A, 99999L, List.of(new OrderCriteria.Line(p1, 2)))));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            assertThat(stockOf(p1)).isEqualTo(10);
        }

        @DisplayName("본인 소유가 아닌 쿠폰이면 FORBIDDEN 을 던지고 재고·쿠폰 모두 보존된다. (AC-07-7)")
        @Test
        void throwsForbidden_whenCouponNotOwned_andPreservesAll() {
            Long p1 = saveProduct("상품1", 1_000L, 10);
            Long couponId = issueCoupon(USER_B, DiscountType.FIXED, 500L); // USER_B 소유

            CoreException result = assertThrows(CoreException.class,
                    () -> orderApplicationService.place(
                            new OrderCriteria.Place(USER_A, couponId, List.of(new OrderCriteria.Line(p1, 2)))));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
            assertThat(stockOf(p1)).isEqualTo(10);
            assertThat(userCouponJpaRepository.findById(couponId).orElseThrow().getStatus())
                    .isEqualTo(CouponStatus.AVAILABLE);
        }

        @DisplayName("이미 사용된 쿠폰을 다시 쓰면 BAD_REQUEST 를 던지고 재고는 보존된다 (재사용 불가). (AC-07-7, AC-07-8)")
        @Test
        void throwsBadRequest_whenCouponAlreadyUsed() {
            Long p1 = saveProduct("상품1", 1_000L, 10);
            Long couponId = issueCoupon(USER_A, DiscountType.FIXED, 500L);

            // 첫 주문에서 쿠폰 사용 완료
            orderApplicationService.place(
                    new OrderCriteria.Place(USER_A, couponId, List.of(new OrderCriteria.Line(p1, 1))));

            // 같은 쿠폰으로 두 번째 주문 시도
            CoreException result = assertThrows(CoreException.class,
                    () -> orderApplicationService.place(
                            new OrderCriteria.Place(USER_A, couponId, List.of(new OrderCriteria.Line(p1, 1)))));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            // 첫 주문 1개 차감분만 반영되고 두 번째 시도는 롤백
            assertThat(stockOf(p1)).isEqualTo(9);
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
                    new OrderCriteria.Place(USER_A, null, List.of(new OrderCriteria.Line(p1, 2))));

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
                    new OrderCriteria.Place(USER_A, null, List.of(new OrderCriteria.Line(p1, 1))));

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
                    new OrderCriteria.Place(USER_A, null, List.of(new OrderCriteria.Line(p1, 1))));
            OrderInfo.Created o2 = orderApplicationService.place(
                    new OrderCriteria.Place(USER_A, null, List.of(new OrderCriteria.Line(p2, 1))));
            orderApplicationService.place(
                    new OrderCriteria.Place(USER_B, null, List.of(new OrderCriteria.Line(p3, 1))));

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
                        new OrderCriteria.Place(USER_A, null, List.of(new OrderCriteria.Line(p1, 1))));
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
                    new OrderCriteria.Place(USER_A, null, List.of(new OrderCriteria.Line(p1, 1))));

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
                    new OrderCriteria.Place(USER_A, null, List.of(new OrderCriteria.Line(p1, 2))));

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
                new OrderCriteria.Place(USER_A, null, List.of(new OrderCriteria.Line(p1, 1))));
        orderApplicationService.place(
                new OrderCriteria.Place(USER_B, null, List.of(new OrderCriteria.Line(p2, 1))));

        PageResult<OrderInfo.ListItem> result = orderApplicationService.getAllOrders(
                new OrderCriteria.AdminSearch(0, 20));

        assertThat(result.content()).hasSize(2);
        assertThat(result.content()).extracting(OrderInfo.ListItem::userId)
                .containsExactlyInAnyOrder(USER_A, USER_B);
    }
}
