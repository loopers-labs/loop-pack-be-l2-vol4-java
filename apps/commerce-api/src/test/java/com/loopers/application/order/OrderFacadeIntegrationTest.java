package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCouponModel;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.outbox.OutboxStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatsModel;
import com.loopers.domain.product.ProductStatsRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderFacadeIntegrationTest {

    private static final String LOGIN_ID = "user01";
    private static final String LOGIN_PW = "Password1!";

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
    private OrderRepository orderRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    @Autowired
    private ProductStatsRepository productStatsRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private UserModel saveUser() {
        return userRepository.save(new UserModel(LOGIN_ID, LOGIN_PW, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor));
    }

    private ProductModel saveProduct(String name, BigDecimal price) {
        BrandModel brand = brandRepository.save(new BrandModel("테스트브랜드"));
        return productRepository.save(new ProductModel(brand.getId(), name, price));
    }

    private void saveStock(Long productId, Long quantity) {
        stockRepository.save(new StockModel(productId, quantity));
    }

    private CouponTemplateModel saveTemplate(BigDecimal minOrderAmount, ZonedDateTime expiredAt) {
        return couponTemplateRepository.save(
                new CouponTemplateModel("테스트 쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000), minOrderAmount, expiredAt));
    }

    private IssuedCouponModel saveIssuedCoupon(Long couponTemplateId, Long userId) {
        return issuedCouponRepository.save(new IssuedCouponModel(couponTemplateId, userId));
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 요청이면 OrderInfo가 반환되고 재고가 차감된다.")
        @Test
        void returnsOrderInfo_andDecreasesStock_whenValidRequest() {
            // given
            saveUser();
            ProductModel product = saveProduct("테스트 상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 5L);
            List<OrderFacade.OrderItemDto> commands = List.of(
                    new OrderFacade.OrderItemDto(product.getId(), 2L)
            );

            // when
            OrderInfo result = orderFacade.createOrder(LOGIN_ID, LOGIN_PW, commands, null);

            // then
            StockModel stock = stockRepository.findByProductId(product.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(result.status()).isEqualTo("PLACED"),
                    () -> assertThat(result.originalPrice()).isEqualByComparingTo(BigDecimal.valueOf(20000)),
                    () -> assertThat(result.discountAmount()).isEqualByComparingTo(BigDecimal.ZERO),
                    () -> assertThat(result.finalPrice()).isEqualByComparingTo(BigDecimal.valueOf(20000)),
                    () -> assertThat(result.items()).hasSize(1),
                    () -> assertThat(result.items().get(0).productName()).isEqualTo("테스트 상품"),
                    () -> assertThat(result.items().get(0).productPrice()).isEqualByComparingTo(BigDecimal.valueOf(10000)),
                    () -> assertThat(stock.getQuantity()).isEqualTo(3L)
            );
        }

        @DisplayName("주문이 생성되면 ORDER_CREATED 아웃박스가 PENDING 상태로 기록된다.")
        @Test
        void recordsOrderCreatedOutbox_whenOrderIsCreated() {
            // given
            saveUser();
            ProductModel product = saveProduct("테스트 상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 5L);
            List<OrderFacade.OrderItemDto> commands = List.of(
                    new OrderFacade.OrderItemDto(product.getId(), 2L)
            );

            // when
            OrderInfo result = orderFacade.createOrder(LOGIN_ID, LOGIN_PW, commands, null);

            // then
            List<OutboxModel> pending = outboxRepository.findAllByStatusOrderByIdAsc(OutboxStatus.PENDING);
            assertAll(
                    () -> assertThat(pending).hasSize(1),
                    () -> assertThat(pending.get(0).getEventType()).isEqualTo("ORDER_CREATED"),
                    () -> assertThat(pending.get(0).getAggregateType()).isEqualTo("Order"),
                    () -> assertThat(pending.get(0).getAggregateId()).isEqualTo(String.valueOf(result.id()))
            );
        }

        @DisplayName("여러 상품을 주문하면 originalPrice가 각 상품의 가격 × 수량 합산으로 계산된다.")
        @Test
        void calculatesOriginalPrice_whenMultipleItemsOrdered() {
            // given
            saveUser();
            BrandModel brand = brandRepository.save(new BrandModel("브랜드"));
            ProductModel productA = productRepository.save(new ProductModel(brand.getId(), "상품A", BigDecimal.valueOf(10000)));
            ProductModel productB = productRepository.save(new ProductModel(brand.getId(), "상품B", BigDecimal.valueOf(5000)));
            saveStock(productA.getId(), 10L);
            saveStock(productB.getId(), 10L);
            List<OrderFacade.OrderItemDto> commands = List.of(
                    new OrderFacade.OrderItemDto(productA.getId(), 2L),
                    new OrderFacade.OrderItemDto(productB.getId(), 3L)
            );

            // when
            OrderInfo result = orderFacade.createOrder(LOGIN_ID, LOGIN_PW, commands, null);

            // then
            assertThat(result.originalPrice()).isEqualByComparingTo(BigDecimal.valueOf(35000));
        }

        @DisplayName("주문 항목에 상품 정보가 스냅샷으로 저장되어, 상품 정보 변경 후에도 주문에는 원래 값이 유지된다.")
        @Test
        void preservesProductSnapshot_whenProductIsUpdatedAfterOrder() {
            // given
            saveUser();
            ProductModel product = saveProduct("원래 상품명", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 5L);
            List<OrderFacade.OrderItemDto> commands = List.of(
                    new OrderFacade.OrderItemDto(product.getId(), 1L)
            );
            OrderInfo orderInfo = orderFacade.createOrder(LOGIN_ID, LOGIN_PW, commands, null);

            // 상품 정보 변경
            product.update("변경된 상품명", BigDecimal.valueOf(99999));
            productRepository.save(product);

            // when
            OrderInfo reloaded = orderFacade.getOrder(LOGIN_ID, LOGIN_PW, orderInfo.id());

            // then
            assertAll(
                    () -> assertThat(reloaded.items().get(0).productName()).isEqualTo("원래 상품명"),
                    () -> assertThat(reloaded.items().get(0).productPrice()).isEqualByComparingTo(BigDecimal.valueOf(10000))
            );
        }

        @DisplayName("존재하지 않는 상품 ID가 포함되면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenProductDoesNotExist() {
            // given
            saveUser();
            List<OrderFacade.OrderItemDto> commands = List.of(
                    new OrderFacade.OrderItemDto(999L, 1L)
            );

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> orderFacade.createOrder(LOGIN_ID, LOGIN_PW, commands, null));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("재고가 부족하면 BAD_REQUEST 예외가 발생하고 재고가 차감되지 않는다.")
        @Test
        void throwsBadRequest_andDoesNotDecreaseStock_whenStockIsInsufficient() {
            // given
            saveUser();
            ProductModel product = saveProduct("테스트 상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 1L);
            List<OrderFacade.OrderItemDto> commands = List.of(
                    new OrderFacade.OrderItemDto(product.getId(), 2L)
            );

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> orderFacade.createOrder(LOGIN_ID, LOGIN_PW, commands, null));

            // then
            StockModel stock = stockRepository.findByProductId(product.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                    () -> assertThat(stock.getQuantity()).isEqualTo(1L)
            );
        }

        @DisplayName("여러 상품 중 하나라도 재고 부족이면 다른 상품의 재고도 차감되지 않고 주문도 저장되지 않는다.")
        @Test
        void rollsBackAllStocks_andDoesNotSaveOrder_whenAnyItemIsInsufficient() {
            // given
            saveUser();
            BrandModel brand = brandRepository.save(new BrandModel("브랜드"));
            ProductModel productA = productRepository.save(new ProductModel(brand.getId(), "상품A", BigDecimal.valueOf(10000)));
            ProductModel productB = productRepository.save(new ProductModel(brand.getId(), "상품B", BigDecimal.valueOf(5000)));
            saveStock(productA.getId(), 5L);
            saveStock(productB.getId(), 1L);
            List<OrderFacade.OrderItemDto> commands = List.of(
                    new OrderFacade.OrderItemDto(productA.getId(), 2L),
                    new OrderFacade.OrderItemDto(productB.getId(), 2L)
            );

            // when
            assertThrows(CoreException.class, () -> orderFacade.createOrder(LOGIN_ID, LOGIN_PW, commands, null));

            // then
            StockModel stockA = stockRepository.findByProductId(productA.getId()).orElseThrow();
            StockModel stockB = stockRepository.findByProductId(productB.getId()).orElseThrow();
            List<OrderInfo> orders = orderFacade.getOrders(LOGIN_ID, LOGIN_PW, LocalDate.now().minusDays(1), LocalDate.now());
            assertAll(
                    () -> assertThat(stockA.getQuantity()).isEqualTo(5L),
                    () -> assertThat(stockB.getQuantity()).isEqualTo(1L),
                    () -> assertThat(orders).isEmpty()
            );
        }

        @DisplayName("인증 실패 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAuthenticationFails() {
            // given
            saveUser();
            List<OrderFacade.OrderItemDto> commands = List.of(
                    new OrderFacade.OrderItemDto(1L, 1L)
            );

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> orderFacade.createOrder(LOGIN_ID, "WrongPass1!", commands, null));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

    }

    @DisplayName("주문 목록을 조회할 때,")
    @Nested
    class GetOrders {

        @DisplayName("기간 내 본인 주문 목록이 반환된다.")
        @Test
        void returnsOrdersInPeriod_forCurrentUser() {
            // given
            saveUser();
            ProductModel product = saveProduct("상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 10L);
            orderFacade.createOrder(LOGIN_ID, LOGIN_PW,
                    List.of(new OrderFacade.OrderItemDto(product.getId(), 1L)), null);

            LocalDate today = LocalDate.now();

            // when
            List<OrderInfo> result = orderFacade.getOrders(LOGIN_ID, LOGIN_PW, today.minusDays(1), today);

            // then
            assertThat(result).hasSize(1);
        }

        @DisplayName("기간 외 주문은 조회되지 않는다.")
        @Test
        void doesNotReturnOrders_outsideQueryPeriod() {
            // given
            saveUser();
            ProductModel product = saveProduct("상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 10L);
            orderFacade.createOrder(LOGIN_ID, LOGIN_PW,
                    List.of(new OrderFacade.OrderItemDto(product.getId(), 1L)), null);

            LocalDate yesterday = LocalDate.now().minusDays(1);

            // when
            List<OrderInfo> result = orderFacade.getOrders(LOGIN_ID, LOGIN_PW, yesterday.minusDays(7), yesterday.minusDays(1));

            // then
            assertThat(result).isEmpty();
        }

        @DisplayName("다른 사용자의 주문은 조회되지 않는다.")
        @Test
        void doesNotReturnOrders_ofOtherUser() {
            // given
            saveUser();
            ProductModel product = saveProduct("상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 10L);
            orderFacade.createOrder(LOGIN_ID, LOGIN_PW,
                    List.of(new OrderFacade.OrderItemDto(product.getId(), 1L)), null);

            String otherId = "other01";
            userRepository.save(new UserModel(otherId, LOGIN_PW, "다른유저", "1991-01-01", "other@example.com", Gender.FEMALE, passwordEncryptor));

            LocalDate today = LocalDate.now();

            // when
            List<OrderInfo> result = orderFacade.getOrders(otherId, LOGIN_PW, today.minusDays(1), today);

            // then
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("주문 단건을 조회할 때,")
    @Nested
    class GetOrder {

        @DisplayName("본인 주문이면 OrderInfo가 반환된다.")
        @Test
        void returnsOrderInfo_whenOwnerRequests() {
            // given
            saveUser();
            ProductModel product = saveProduct("상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 5L);
            OrderInfo created = orderFacade.createOrder(LOGIN_ID, LOGIN_PW,
                    List.of(new OrderFacade.OrderItemDto(product.getId(), 1L)), null);

            // when
            OrderInfo result = orderFacade.getOrder(LOGIN_ID, LOGIN_PW, created.id());

            // then
            assertThat(result.id()).isEqualTo(created.id());
        }

        @DisplayName("다른 사용자의 주문을 조회하면 FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenAccessingAnotherUsersOrder() {
            // given
            saveUser();
            ProductModel product = saveProduct("상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 5L);
            OrderInfo created = orderFacade.createOrder(LOGIN_ID, LOGIN_PW,
                    List.of(new OrderFacade.OrderItemDto(product.getId(), 1L)), null);

            String otherId = "other01";
            userRepository.save(new UserModel(otherId, LOGIN_PW, "다른유저", "1991-01-01", "other@example.com", Gender.FEMALE, passwordEncryptor));

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> orderFacade.getOrder(otherId, LOGIN_PW, created.id()));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }

        @DisplayName("존재하지 않는 주문 ID이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenOrderDoesNotExist() {
            // given
            saveUser();

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> orderFacade.getOrder(LOGIN_ID, LOGIN_PW, 999L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰을 적용하여 주문을 생성할 때,")
    @Nested
    class CreateOrderWithCoupon {

        @DisplayName("유효한 FIXED 쿠폰을 적용하면 할인이 적용된 주문이 생성되고, 쿠폰은 USED 상태로 변경된다.")
        @Test
        void appliesFixedCouponDiscount_whenValidCouponProvided() {
            // given
            UserModel user = saveUser();
            ProductModel product = saveProduct("상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 5L);

            CouponTemplateModel template = saveTemplate(null, ZonedDateTime.now().plusDays(1));
            IssuedCouponModel issued = saveIssuedCoupon(template.getId(), user.getId());

            List<OrderFacade.OrderItemDto> commands = List.of(
                    new OrderFacade.OrderItemDto(product.getId(), 2L)
            );

            // when
            OrderInfo result = orderFacade.createOrder(LOGIN_ID, LOGIN_PW, commands, issued.getId());

            // then
            IssuedCouponModel usedCoupon = issuedCouponRepository.findById(issued.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(result.originalPrice()).isEqualByComparingTo(BigDecimal.valueOf(20000)),
                    () -> assertThat(result.discountAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000)),
                    () -> assertThat(result.finalPrice()).isEqualByComparingTo(BigDecimal.valueOf(19000)),
                    () -> assertThat(orderRepository.findById(result.id())).isPresent(),
                    () -> assertThat(usedCoupon.getStatus()).isEqualTo(com.loopers.domain.coupon.CouponStatus.USED)
            );
        }

        @DisplayName("존재하지 않는 issuedCouponId로 주문하면 NOT_FOUND 예외가 발생하고 주문이 저장되지 않는다.")
        @Test
        void throwsNotFoundException_whenIssuedCouponDoesNotExist() {
            // given
            saveUser();
            ProductModel product = saveProduct("상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 5L);

            List<OrderFacade.OrderItemDto> commands = List.of(
                    new OrderFacade.OrderItemDto(product.getId(), 1L)
            );

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> orderFacade.createOrder(LOGIN_ID, LOGIN_PW, commands, 999L));

            // then
            List<OrderInfo> orders = orderFacade.getOrders(LOGIN_ID, LOGIN_PW, LocalDate.now().minusDays(1), LocalDate.now());
            assertAll(
                    () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND),
                    () -> assertThat(orders).isEmpty()
            );
        }

        @DisplayName("만료된 쿠폰을 적용하면 BAD_REQUEST 예외가 발생하고 주문이 저장되지 않는다.")
        @Test
        void throwsBadRequest_whenCouponIsExpired() {
            // given
            UserModel user = saveUser();
            ProductModel product = saveProduct("상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 5L);

            CouponTemplateModel template = saveTemplate(null, ZonedDateTime.now().minusDays(1));
            IssuedCouponModel issued = saveIssuedCoupon(template.getId(), user.getId());

            List<OrderFacade.OrderItemDto> commands = List.of(
                    new OrderFacade.OrderItemDto(product.getId(), 1L)
            );

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> orderFacade.createOrder(LOGIN_ID, LOGIN_PW, commands, issued.getId()));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("최소 주문 금액 미충족 쿠폰을 적용하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenMinOrderAmountNotMet() {
            // given
            UserModel user = saveUser();
            ProductModel product = saveProduct("상품", BigDecimal.valueOf(5000));
            saveStock(product.getId(), 5L);

            CouponTemplateModel template = saveTemplate(BigDecimal.valueOf(10000), ZonedDateTime.now().plusDays(1));
            IssuedCouponModel issued = saveIssuedCoupon(template.getId(), user.getId());

            List<OrderFacade.OrderItemDto> commands = List.of(
                    new OrderFacade.OrderItemDto(product.getId(), 1L)
            );

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> orderFacade.createOrder(LOGIN_ID, LOGIN_PW, commands, issued.getId()));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("다른 사용자의 쿠폰을 적용하면 FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenCouponOwnedByAnotherUser() {
            // given
            saveUser();
            ProductModel product = saveProduct("상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 5L);

            String otherId = "other01";
            UserModel otherUser = userRepository.save(new UserModel(otherId, LOGIN_PW, "다른유저", "1991-01-01", "other@example.com", Gender.FEMALE, passwordEncryptor));
            CouponTemplateModel template = saveTemplate(null, ZonedDateTime.now().plusDays(1));
            IssuedCouponModel issued = saveIssuedCoupon(template.getId(), otherUser.getId());

            List<OrderFacade.OrderItemDto> commands = List.of(
                    new OrderFacade.OrderItemDto(product.getId(), 1L)
            );

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> orderFacade.createOrder(LOGIN_ID, LOGIN_PW, commands, issued.getId()));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }

        @DisplayName("이미 사용된 쿠폰을 적용하면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenCouponAlreadyUsed() {
            // given
            UserModel user = saveUser();
            ProductModel product = saveProduct("상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 10L);

            CouponTemplateModel template = saveTemplate(null, ZonedDateTime.now().plusDays(1));
            IssuedCouponModel issued = saveIssuedCoupon(template.getId(), user.getId());

            List<OrderFacade.OrderItemDto> commands = List.of(
                    new OrderFacade.OrderItemDto(product.getId(), 1L)
            );
            orderFacade.createOrder(LOGIN_ID, LOGIN_PW, commands, issued.getId());

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> orderFacade.createOrder(LOGIN_ID, LOGIN_PW, commands, issued.getId()));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
