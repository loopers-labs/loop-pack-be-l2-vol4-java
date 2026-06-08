package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.domain.user.Email;
import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserName;
import com.loopers.domain.user.UserRepository;
import com.loopers.infrastructure.order.OrderItemJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderFacadeIntegrationTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private OrderItemJpaRepository orderItemJpaRepository;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long product1Id;
    private Long product2Id;

    @BeforeEach
    void setUp() {
        UserModel user = userRepository.save(new UserModel(
            new LoginId("loopers01"), "$2a$10$dummyEncodedHash",
            new UserName("홍길동"), LocalDate.of(2002, 5, 11), new Email("test@loopers.com")
        ));
        userId = user.getId();

        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        ProductModel p1 = productRepository.save(new ProductModel(brand.getId(), "후드", "포근함", 50_000L));
        ProductModel p2 = productRepository.save(new ProductModel(brand.getId(), "맨투맨", "심플", 30_000L));
        product1Id = p1.getId();
        product2Id = p2.getId();
        stockRepository.save(new StockModel(product1Id, 10));
        stockRepository.save(new StockModel(product2Id, 5));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderLineCommand line(Long productId, int quantity) {
        return new OrderLineCommand(productId, quantity);
    }

    @DisplayName("주문 생성 시")
    @Nested
    class PlaceOrder {

        @DisplayName("쿠폰 없이 정상 주문이면 status=CREATED로 저장되고 재고 차감 + items가 cascade로 영속된다")
        @Test
        void persistsOrderWithItems_andDecreasesStock() {
            OrderInfo info = orderFacade.placeOrder(userId, List.of(
                line(product1Id, 2),
                line(product2Id, 1)
            ), null);

            assertAll(
                () -> assertThat(info.status()).isEqualTo(OrderStatus.CREATED),
                () -> assertThat(info.totalAmount()).isEqualTo(50_000L * 2 + 30_000L),
                () -> assertThat(info.discountAmount()).isZero(),
                () -> assertThat(info.finalAmount()).isEqualTo(130_000L),
                () -> assertThat(info.items()).hasSize(2),
                () -> assertThat(stockRepository.findByProductId(product1Id).orElseThrow().getQuantity()).isEqualTo(8),
                () -> assertThat(stockRepository.findByProductId(product2Id).orElseThrow().getQuantity()).isEqualTo(4),
                () -> assertThat(orderJpaRepository.count()).isEqualTo(1),
                () -> assertThat(orderItemJpaRepository.count()).isEqualTo(2)
            );
        }

        @DisplayName("존재하지 않는 유저로 주문하면 NOT_FOUND이고 Order row도 OrderItem도 생기지 않는다")
        @Test
        void throwsNotFound_andNoRows_whenUserDoesNotExist() {
            CoreException ex = assertThrows(CoreException.class, () ->
                orderFacade.placeOrder(99_999L, List.of(line(product1Id, 1)), null)
            );

            assertAll(
                () -> assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND),
                () -> assertThat(orderJpaRepository.count()).isZero(),
                () -> assertThat(orderItemJpaRepository.count()).isZero(),
                () -> assertThat(stockRepository.findByProductId(product1Id).orElseThrow().getQuantity()).isEqualTo(10)
            );
        }

        @DisplayName("존재하지 않는 상품을 포함하면 NOT_FOUND이고 Order row도 OrderItem도 생기지 않는다")
        @Test
        void throwsNotFound_andNoRows_whenProductDoesNotExist() {
            CoreException ex = assertThrows(CoreException.class, () ->
                orderFacade.placeOrder(userId, List.of(
                    line(product1Id, 1),
                    line(99_999L, 1)
                ), null)
            );

            assertAll(
                () -> assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND),
                () -> assertThat(orderJpaRepository.count()).isZero(),
                () -> assertThat(orderItemJpaRepository.count()).isZero(),
                () -> assertThat(stockRepository.findByProductId(product1Id).orElseThrow().getQuantity()).isEqualTo(10)
            );
        }

        @DisplayName("재고 부족이면 CONFLICT이고 전체가 롤백된다 (Order/OrderItem/재고 모두 변경 없음)")
        @Test
        void throwsConflict_andRollsBackEverything_whenStockIsInsufficient() {
            CoreException ex = assertThrows(CoreException.class, () ->
                orderFacade.placeOrder(userId, List.of(
                    line(product1Id, 1),
                    line(product2Id, 10)
                ), null)
            );

            assertAll(
                () -> assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(orderJpaRepository.count()).isZero(),
                () -> assertThat(orderItemJpaRepository.count()).isZero(),
                () -> assertThat(stockRepository.findByProductId(product1Id).orElseThrow().getQuantity()).isEqualTo(10),
                () -> assertThat(stockRepository.findByProductId(product2Id).orElseThrow().getQuantity()).isEqualTo(5)
            );
        }

        @DisplayName("같은 상품을 여러 line으로 보내도 합산된 수량으로 재고가 한 번에 차감된다")
        @Test
        void aggregatesSameProductLines_forStockDecrease() {
            OrderInfo info = orderFacade.placeOrder(userId, List.of(
                line(product1Id, 2),
                line(product1Id, 3)
            ), null);

            assertAll(
                () -> assertThat(info.status()).isEqualTo(OrderStatus.CREATED),
                () -> assertThat(info.items()).hasSize(2),
                () -> assertThat(stockRepository.findByProductId(product1Id).orElseThrow().getQuantity()).isEqualTo(5)
            );
        }
    }

    @DisplayName("주문 전체 금액에 쿠폰 적용 시")
    @Nested
    class PlaceOrderWithCoupon {

        @DisplayName("쿠폰을 적용하면 주문 전체 금액 기준으로 할인되고 쿠폰이 USED로 추적된다")
        @Test
        void appliesCouponToOrderTotal_andTracksUsage() {
            // given - 정률 10% 쿠폰
            CouponTemplate template = couponTemplateRepository.save(
                new CouponTemplate("10% 할인", CouponType.RATE, 10L, null, ZonedDateTime.now().plusDays(7)));
            IssuedCoupon coupon = issuedCouponRepository.save(new IssuedCoupon(userId, template.getId()));

            // when - 주문 전체 130,000원에 쿠폰 적용
            OrderInfo info = orderFacade.placeOrder(userId, List.of(
                line(product1Id, 2),   // 100,000
                line(product2Id, 1)    // 30,000
            ), coupon.getId());

            // then - 130,000의 10% = 13,000 할인
            assertAll(
                () -> assertThat(info.totalAmount()).isEqualTo(130_000L),
                () -> assertThat(info.discountAmount()).isEqualTo(13_000L),
                () -> assertThat(info.finalAmount()).isEqualTo(117_000L),
                () -> assertThat(info.issuedCouponId()).isEqualTo(coupon.getId()),
                () -> assertThat(issuedCouponRepository.findById(coupon.getId()).orElseThrow().getStatus())
                    .isEqualTo(CouponStatus.USED)
            );
        }

        @DisplayName("이미 사용된 쿠폰으로 주문하면 CONFLICT이고 재고·주문이 모두 롤백된다")
        @Test
        void rollsBackEverything_whenCouponAlreadyUsed() {
            // given - 쿠폰을 첫 주문에서 사용 처리
            CouponTemplate template = couponTemplateRepository.save(
                new CouponTemplate("3천원 할인", CouponType.FIXED, 3_000L, null, ZonedDateTime.now().plusDays(7)));
            IssuedCoupon coupon = issuedCouponRepository.save(new IssuedCoupon(userId, template.getId()));
            orderFacade.placeOrder(userId, List.of(line(product1Id, 1)), coupon.getId());

            // when - 같은 쿠폰으로 재주문
            CoreException ex = assertThrows(CoreException.class, () ->
                orderFacade.placeOrder(userId, List.of(line(product2Id, 1)), coupon.getId())
            );

            // then - 두 번째 주문은 전체 롤백 (product2 재고 유지, 주문은 첫 건만)
            assertAll(
                () -> assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(stockRepository.findByProductId(product2Id).orElseThrow().getQuantity()).isEqualTo(5),
                () -> assertThat(orderJpaRepository.count()).isEqualTo(1)
            );
        }

        @DisplayName("만료된 쿠폰으로 주문하면 CONFLICT이고 재고·주문이 모두 롤백된다")
        @Test
        void rollsBackEverything_whenCouponExpired() {
            // given - 이미 만료된 쿠폰
            CouponTemplate template = couponTemplateRepository.save(
                new CouponTemplate("만료 쿠폰", CouponType.FIXED, 3_000L, null, ZonedDateTime.now().minusDays(1)));
            IssuedCoupon coupon = issuedCouponRepository.save(new IssuedCoupon(userId, template.getId()));

            // when
            CoreException ex = assertThrows(CoreException.class, () ->
                orderFacade.placeOrder(userId, List.of(line(product1Id, 1)), coupon.getId())
            );

            // then
            assertAll(
                () -> assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(orderJpaRepository.count()).isZero(),
                () -> assertThat(stockRepository.findByProductId(product1Id).orElseThrow().getQuantity()).isEqualTo(10),
                () -> assertThat(issuedCouponRepository.findById(coupon.getId()).orElseThrow().getStatus())
                    .isEqualTo(CouponStatus.AVAILABLE)
            );
        }

        @DisplayName("존재하지 않는 쿠폰으로 주문하면 NOT_FOUND이고 재고·주문이 모두 롤백된다")
        @Test
        void rollsBackEverything_whenCouponNotFound() {
            CoreException ex = assertThrows(CoreException.class, () ->
                orderFacade.placeOrder(userId, List.of(line(product1Id, 1)), 99_999L)
            );

            assertAll(
                () -> assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND),
                () -> assertThat(orderJpaRepository.count()).isZero(),
                () -> assertThat(stockRepository.findByProductId(product1Id).orElseThrow().getQuantity()).isEqualTo(10)
            );
        }

        @DisplayName("최소주문금액은 주문 전체 합계로 판정한다 — 합계가 조건을 충족하면 할인된다")
        @Test
        void evaluatesMinOrderAmount_onOrderTotal_satisfied() {
            // given - 최소주문금액 120,000 쿠폰. 단일 상품은 못 넘기지만 합계(130,000)는 충족
            CouponTemplate template = couponTemplateRepository.save(
                new CouponTemplate("12만원 이상 5천원", CouponType.FIXED, 5_000L, 120_000L, ZonedDateTime.now().plusDays(7)));
            IssuedCoupon coupon = issuedCouponRepository.save(new IssuedCoupon(userId, template.getId()));

            OrderInfo info = orderFacade.placeOrder(userId, List.of(
                line(product1Id, 2),   // 100,000
                line(product2Id, 1)    // 30,000 → 합계 130,000
            ), coupon.getId());

            assertAll(
                () -> assertThat(info.totalAmount()).isEqualTo(130_000L),
                () -> assertThat(info.discountAmount()).isEqualTo(5_000L),
                () -> assertThat(info.finalAmount()).isEqualTo(125_000L)
            );
        }

        @DisplayName("최소주문금액은 주문 전체 합계로 판정한다 — 합계가 조건에 못 미치면 CONFLICT로 롤백된다")
        @Test
        void evaluatesMinOrderAmount_onOrderTotal_notSatisfied() {
            // given - 최소주문금액 200,000 쿠폰. 합계 130,000은 미달
            CouponTemplate template = couponTemplateRepository.save(
                new CouponTemplate("20만원 이상 5천원", CouponType.FIXED, 5_000L, 200_000L, ZonedDateTime.now().plusDays(7)));
            IssuedCoupon coupon = issuedCouponRepository.save(new IssuedCoupon(userId, template.getId()));

            CoreException ex = assertThrows(CoreException.class, () ->
                orderFacade.placeOrder(userId, List.of(
                    line(product1Id, 2),
                    line(product2Id, 1)
                ), coupon.getId())
            );

            assertAll(
                () -> assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(orderJpaRepository.count()).isZero(),
                () -> assertThat(issuedCouponRepository.findById(coupon.getId()).orElseThrow().getStatus())
                    .isEqualTo(CouponStatus.AVAILABLE)
            );
        }
    }
}
