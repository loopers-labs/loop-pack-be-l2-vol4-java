package com.loopers.order.application;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.infrastructure.BrandJpaRepository;
import com.loopers.coupon.application.MemberCouponService;
import com.loopers.coupon.domain.Coupon;
import com.loopers.coupon.domain.CouponState;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.MemberCoupon;
import com.loopers.coupon.infrastructure.CouponJpaRepository;
import com.loopers.inventory.domain.Inventory;
import com.loopers.inventory.infrastructure.InventoryJpaRepository;
import com.loopers.member.domain.Member;
import com.loopers.member.infrastructure.MemberJpaRepository;
import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderLine;
import com.loopers.order.infrastructure.OrderJpaRepository;
import com.loopers.product.domain.Product;
import com.loopers.product.infrastructure.ProductJpaRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderFacadeTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired private OrderFacade orderFacade;
    @Autowired private MemberCouponService memberCouponService;
    @Autowired private MemberJpaRepository memberJpaRepository;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private InventoryJpaRepository inventoryJpaRepository;
    @Autowired private CouponJpaRepository couponJpaRepository;
    @Autowired private OrderJpaRepository orderJpaRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long memberId;
    private Long productId1;
    private Long productId2;

    @BeforeEach
    void setUp() {
        Member member = memberJpaRepository.save(new Member("member01", "pw123456"));
        Brand brand = brandJpaRepository.save(new Brand("브랜드", "설명"));
        Product p1 = productJpaRepository.save(new Product(brand.getId(), "A", "설명", 1_000L));
        Product p2 = productJpaRepository.save(new Product(brand.getId(), "B", "설명", 2_000L));
        memberId = member.getId();
        productId1 = p1.getId();
        productId2 = p2.getId();
        inventoryJpaRepository.save(new Inventory(productId1, 10));
        inventoryJpaRepository.save(new Inventory(productId2, 5));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private int stockOf(Long productId) {
        return inventoryJpaRepository.findByProductId(productId).orElseThrow().getAvailableQuantity();
    }

    private Long issueCoupon(CouponType type, long value, Long minOrderAmount) {
        Coupon coupon =
            couponJpaRepository.save(
                new Coupon("쿠폰", type, value, minOrderAmount, ZonedDateTime.now(SEOUL).plusDays(1)));
        MemberCoupon issued = memberCouponService.issue(memberId, coupon.getId());
        return issued.getId();
    }

    @DisplayName("정상 주문 흐름에서,")
    @Nested
    class Success {
        @DisplayName("여러 상품을 주문하면 재고가 차감되고 총액이 계산된다.")
        @Test
        void createsOrder_andDeductsStock() {
            OrderInfo info =
                orderFacade.createOrder(
                    memberId,
                    List.of(new OrderLine(productId1, 2), new OrderLine(productId2, 1)),
                    null);

            assertThat(info.totalAmount()).isEqualTo(2 * 1_000L + 2_000L);
            assertThat(info.paymentAmount()).isEqualTo(info.totalAmount());
            assertThat(info.discountAmount()).isZero();
            assertThat(info.items()).hasSize(2);
            assertThat(stockOf(productId1)).isEqualTo(8);
            assertThat(stockOf(productId2)).isEqualTo(4);
        }

        @DisplayName("쿠폰을 적용하면 할인 금액과 결제 예정 금액이 계산되고 쿠폰이 USED 로 전이된다.")
        @Test
        void appliesCoupon() {
            Long memberCouponId = issueCoupon(CouponType.FIXED, 500L, null);

            OrderInfo info =
                orderFacade.createOrder(memberId, List.of(new OrderLine(productId1, 2)), memberCouponId);

            assertThat(info.totalAmount()).isEqualTo(2_000L);
            assertThat(info.discountAmount()).isEqualTo(500L);
            assertThat(info.paymentAmount()).isEqualTo(1_500L);
            assertThat(info.appliedMemberCouponId()).isEqualTo(memberCouponId);
            assertThat(memberCouponService.getMyCoupons(memberId).get(0).getState())
                .isEqualTo(CouponState.USED);
        }

        @DisplayName("주문 후 본인 주문 상세를 조회할 수 있다.")
        @Test
        void canReadOwnOrder() {
            OrderInfo created =
                orderFacade.createOrder(memberId, List.of(new OrderLine(productId1, 1)), null);
            OrderInfo found = orderFacade.getMyOrder(memberId, created.id());
            assertThat(found.id()).isEqualTo(created.id());
        }
    }

    @DisplayName("예외 주문 흐름에서,")
    @Nested
    class Failure {
        @DisplayName("존재하지 않는 회원이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenMemberMissing() {
            CoreException result =
                assertThrows(
                    CoreException.class,
                    () -> orderFacade.createOrder(999L, List.of(new OrderLine(productId1, 1)), null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 상품이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductMissing() {
            CoreException result =
                assertThrows(
                    CoreException.class,
                    () -> orderFacade.createOrder(memberId, List.of(new OrderLine(999L, 1)), null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("재고가 부족하면 CONFLICT 예외가 발생하고 주문이 생성되지 않는다.")
        @Test
        void throwsConflict_whenStockInsufficient() {
            CoreException result =
                assertThrows(
                    CoreException.class,
                    () ->
                        orderFacade.createOrder(
                            memberId, List.of(new OrderLine(productId2, 99)), null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            assertThat(orderFacade.getMyOrders(memberId, null, null)).isEmpty();
        }

        @DisplayName("타 회원 소유 쿠폰으로 주문하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponNotOwned() {
            Coupon coupon =
                couponJpaRepository.save(
                    new Coupon("쿠폰", CouponType.FIXED, 500L, null, ZonedDateTime.now(SEOUL).plusDays(1)));
            MemberCoupon othersCoupon = memberCouponService.issue(999L, coupon.getId());

            CoreException result =
                assertThrows(
                    CoreException.class,
                    () ->
                        orderFacade.createOrder(
                            memberId, List.of(new OrderLine(productId1, 1)), othersCoupon.getId()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("타인의 주문은 조회할 수 없어 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenReadingOthersOrder() {
            OrderInfo created =
                orderFacade.createOrder(memberId, List.of(new OrderLine(productId1, 1)), null);
            CoreException result =
                assertThrows(CoreException.class, () -> orderFacade.getMyOrder(999L, created.id()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("주문 목록 날짜 필터에서,")
    @Nested
    class DateFilter {

        /** 주문을 저장한 뒤 created_at 을 지정 날짜로 갱신한다. (createdAt 은 JPA 가 채우므로 직접 지정 불가) */
        private Long seedOrder(String date) {
            Order order = orderJpaRepository.save(Order.create(memberId));
            ZonedDateTime createdAt = ZonedDateTime.of(LocalDate.parse(date).atStartOfDay(), SEOUL);
            jdbcTemplate.update(
                "UPDATE orders SET created_at = ? WHERE id = ?",
                Timestamp.from(createdAt.toInstant()),
                order.getId());
            return order.getId();
        }

        @DisplayName("startAt/endAt 범위 내 주문만 반환한다.")
        @Test
        void filtersByDateRange() {
            Long inRange1 = seedOrder("2026-02-01");
            Long inRange2 = seedOrder("2026-02-05");
            seedOrder("2026-02-20");

            List<OrderInfo> result =
                orderFacade.getMyOrders(
                    memberId, LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-10"));

            assertThat(result)
                .extracting(OrderInfo::id)
                .containsExactlyInAnyOrder(inRange1, inRange2);
        }

        @DisplayName("날짜 파라미터가 없으면 전체 주문을 반환한다.")
        @Test
        void returnsAll_whenNoDateGiven() {
            seedOrder("2026-02-01");
            seedOrder("2026-02-20");

            List<OrderInfo> result = orderFacade.getMyOrders(memberId, null, null);

            assertThat(result).hasSize(2);
        }
    }
}
