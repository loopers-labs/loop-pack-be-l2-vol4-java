package com.loopers.domain.order;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.DiscountPolicy;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderDomainServiceTest {

    private static final ZonedDateTime NOW = ZonedDateTime.now();

    private final OrderDomainService orderDomainService = new OrderDomainService();

    // 도메인 서비스는 Product.getId() 로 라인을 매칭하므로, 영속 없이도 식별자가 필요하다.
    // 통합/영속 컨텍스트가 아닌 순수 단위 테스트라 id 를 reflection 으로 주입한다.
    private static Product product(long id, long price) {
        Product product = Product.create(1L, "상품" + id, Money.of(price));
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    // 재고는 별도 애그리거트(Inventory). productId 로 매칭되므로 id 주입은 불필요.
    private static Inventory inventory(long productId, int stock) {
        return Inventory.create(productId, stock);
    }

    // 발급 시점 스냅샷을 가진 사용 가능한 내 쿠폰을 만든다(영속 없이 도메인 단위 테스트용).
    private static UserCoupon coupon(long ownerUserId, DiscountType type, long value) {
        CouponTemplate template = CouponTemplate.create("쿠폰", DiscountPolicy.of(type, value), 30);
        ReflectionTestUtils.setField(template, "id", 1L);
        return UserCoupon.issue(ownerUserId, template, NOW);
    }

    @DisplayName("OrderDomainService.create 가 ")
    @Nested
    class Create {

        @DisplayName("동일 productId 가 라인에 중복되면 수량을 합산해 단일 OrderItem 으로 만든다.")
        @Test
        void mergesDuplicateProductLines() {
            // arrange
            Product a = product(101L, 1_000L);
            Product b = product(102L, 2_000L);
            List<Inventory> inventories = List.of(inventory(101L, 10), inventory(102L, 10));
            List<OrderCommand.OrderLine> lines = List.of(
                    OrderCommand.OrderLine.of(101L, 2),
                    OrderCommand.OrderLine.of(102L, 1),
                    OrderCommand.OrderLine.of(101L, 3)
            );

            // act
            Order order = orderDomainService.create(10L, List.of(a, b), inventories, lines, null, NOW);

            // assert — 합산된 항목 2개 (101→5, 102→1), 총액 = 1000*5 + 2000*1 = 7000
            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getItems())
                    .extracting(OrderItem::getProductId, OrderItem::getQuantity)
                    .containsExactlyInAnyOrder(Tuple.tuple(101L, 5), Tuple.tuple(102L, 1));
            assertThat(order.getTotalAmount().getAmount()).isEqualTo(7_000L);
        }

        @DisplayName("OrderItem 은 주문 시점의 productName / unitPrice 스냅샷을 보관한다.")
        @Test
        void snapshotsProductNameAndUnitPrice() {
            // arrange
            Product a = product(101L, 1_500L);
            List<Inventory> inventories = List.of(inventory(101L, 10));
            List<OrderCommand.OrderLine> lines = List.of(OrderCommand.OrderLine.of(101L, 2));

            // act
            Order order = orderDomainService.create(10L, List.of(a), inventories, lines, null, NOW);

            // assert
            OrderItem item = order.getItems().get(0);
            assertThat(item.getProductName()).isEqualTo("상품101");
            assertThat(item.getUnitPrice().getAmount()).isEqualTo(1_500L);
        }

        @DisplayName("주문 성공 시 해당 상품의 재고가 수량만큼 차감된다.")
        @Test
        void decreasesInventory() {
            Product a = product(101L, 1_000L);
            Inventory inv = inventory(101L, 10);
            List<OrderCommand.OrderLine> lines = List.of(OrderCommand.OrderLine.of(101L, 3));

            orderDomainService.create(10L, List.of(a), List.of(inv), lines, null, NOW);

            assertThat(inv.getQuantity()).isEqualTo(7);
        }

        @DisplayName("입력 products 에 없는 productId 가 라인에 있으면 NOT_FOUND.")
        @Test
        void throwsNotFound_whenProductMissing() {
            Product a = product(101L, 1_000L);
            List<Inventory> inventories = List.of(inventory(101L, 10));
            List<OrderCommand.OrderLine> lines = List.of(
                    OrderCommand.OrderLine.of(101L, 1),
                    OrderCommand.OrderLine.of(999L, 1)
            );

            CoreException result = assertThrows(CoreException.class,
                    () -> orderDomainService.create(10L, List.of(a), inventories, lines, null, NOW));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("재고 정보가 없는 productId 가 라인에 있으면 NOT_FOUND.")
        @Test
        void throwsNotFound_whenInventoryMissing() {
            Product a = product(101L, 1_000L);
            List<OrderCommand.OrderLine> lines = List.of(OrderCommand.OrderLine.of(101L, 1));

            CoreException result = assertThrows(CoreException.class,
                    () -> orderDomainService.create(10L, List.of(a), List.of(), lines, null, NOW));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("userId 가 null 이면 BAD_REQUEST.")
        @Test
        void throwsBadRequest_whenUserIdNull() {
            Product a = product(101L, 1_000L);
            List<Inventory> inventories = List.of(inventory(101L, 10));
            List<OrderCommand.OrderLine> lines = List.of(OrderCommand.OrderLine.of(101L, 1));
            CoreException result = assertThrows(CoreException.class,
                    () -> orderDomainService.create(null, List.of(a), inventories, lines, null, NOW));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("lines 가 비어있으면 BAD_REQUEST.")
        @Test
        void throwsBadRequest_whenLinesEmpty() {
            CoreException result = assertThrows(CoreException.class,
                    () -> orderDomainService.create(10L, List.of(), List.of(), List.of(), null, NOW));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("정액 쿠폰을 지정하면 할인액이 최종 금액에 반영된다. (AC-07-6)")
        @Test
        void appliesFixedCouponDiscount() {
            Product a = product(101L, 1_000L);
            List<Inventory> inventories = List.of(inventory(101L, 10));
            List<OrderCommand.OrderLine> lines = List.of(OrderCommand.OrderLine.of(101L, 2)); // 2_000
            UserCoupon userCoupon = coupon(10L, DiscountType.FIXED, 500L);

            Order order = orderDomainService.create(10L, List.of(a), inventories, lines, userCoupon, NOW);

            assertThat(order.getOriginalAmount().getAmount()).isEqualTo(2_000L);
            assertThat(order.getDiscountAmount().getAmount()).isEqualTo(500L);
            assertThat(order.getTotalAmount().getAmount()).isEqualTo(1_500L);
        }

        @DisplayName("정률 쿠폰을 지정하면 원 단위 미만은 절사한 할인액이 반영된다. (AC-07-6)")
        @Test
        void appliesRateCouponDiscount_withFloor() {
            Product a = product(101L, 1_050L);
            List<Inventory> inventories = List.of(inventory(101L, 10));
            List<OrderCommand.OrderLine> lines = List.of(OrderCommand.OrderLine.of(101L, 1)); // 1_050
            UserCoupon userCoupon = coupon(10L, DiscountType.RATE, 10L); // 10% = 105

            Order order = orderDomainService.create(10L, List.of(a), inventories, lines, userCoupon, NOW);

            assertThat(order.getDiscountAmount().getAmount()).isEqualTo(105L);
            assertThat(order.getTotalAmount().getAmount()).isEqualTo(945L);
        }

        @DisplayName("본인 소유가 아닌 쿠폰을 지정하면 FORBIDDEN 이고 재고는 변동되지 않는다. (AC-07-7)")
        @Test
        void throwsForbidden_whenCouponNotOwned_andRollsBackStock() {
            Product a = product(101L, 1_000L);
            Inventory inv = inventory(101L, 10);
            List<OrderCommand.OrderLine> lines = List.of(OrderCommand.OrderLine.of(101L, 2));
            UserCoupon othersCoupon = coupon(99L, DiscountType.FIXED, 500L); // 소유자 99, 주문자 10

            CoreException result = assertThrows(CoreException.class,
                    () -> orderDomainService.create(10L, List.of(a), List.of(inv), lines, othersCoupon, NOW));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
            assertThat(inv.getQuantity()).isEqualTo(10);
        }

        @DisplayName("이미 사용된 쿠폰을 지정하면 BAD_REQUEST 이고 재고는 변동되지 않는다. (AC-07-7)")
        @Test
        void throwsBadRequest_whenCouponAlreadyUsed_andRollsBackStock() {
            Product a = product(101L, 1_000L);
            Inventory inv = inventory(101L, 10);
            List<OrderCommand.OrderLine> lines = List.of(OrderCommand.OrderLine.of(101L, 2));
            UserCoupon used = coupon(10L, DiscountType.FIXED, 500L);
            used.use(777L, NOW); // 다른 주문에서 이미 사용

            CoreException result = assertThrows(CoreException.class,
                    () -> orderDomainService.create(10L, List.of(a), List.of(inv), lines, used, NOW));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(inv.getQuantity()).isEqualTo(10);
        }
    }
}
