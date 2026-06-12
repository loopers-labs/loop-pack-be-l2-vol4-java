package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.coupon.CouponDiscount;
import com.loopers.domain.coupon.CouponExpiry;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.coupon.enums.CouponType;
import com.loopers.domain.coupon.enums.UserCouponStatus;
import com.loopers.domain.order.OrderItemInput;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.enums.OrderStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.ProductStockRepository;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.ProductName;
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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderFacadeIntegrationTest {

    @Autowired private OrderFacade orderFacade;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductStockRepository productStockRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private CouponRepository couponRepository;
    @Autowired private UserCouponRepository userCouponRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 1L;

    private String orderNumber() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return LocalDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + suffix;
    }

    private ProductStockModel stock;
    private UserCouponModel userCoupon;

    private UserCouponModel saveFixedCoupon(long discountAmount) {
        CouponModel coupon = couponRepository.save(new CouponModel(
                "테스트쿠폰",
                new CouponDiscount(CouponType.FIXED, discountAmount, null),
                new CouponExpiry(ZonedDateTime.now().plusDays(7))
        ));
        return userCouponRepository.save(new UserCouponModel(USER_ID, coupon));
    }

    @BeforeEach
    void setUp() {
        BrandModel brand = brandRepository.save(new BrandModel("테스트브랜드"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), new ProductName("테스트상품")));
        stock = productStockRepository.save(new ProductStockModel(product, new Price(10000L), 10));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문 생성(createOrder) 시,")
    @Nested
    class CreateOrder {

        @DisplayName("주문이 생성되고, 재고가 감소한다.")
        @Test
        void createsOrderAndDecreasesStock() {
            List<OrderItemInput> inputs = List.of(new OrderItemInput(stock.getId(), 3));

            OrderInfo result = orderFacade.createOrder(orderNumber(), USER_ID, inputs, null);

            assertThat(result.totalAmount()).isEqualTo(30000L);
            assertThat(result.items()).hasSize(1);
            assertThat(result.status()).isEqualTo(OrderStatus.REQUESTED.getDescription());

            int remainingStock = productStockRepository.findById(stock.getId()).get().getStockQuantity().getValue();
            assertThat(remainingStock).isEqualTo(7);
        }

        @DisplayName("재고가 부족하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsInsufficient() {
            List<OrderItemInput> inputs = List.of(new OrderItemInput(stock.getId(), 100));

            CoreException exception = assertThrows(CoreException.class,
                    () -> orderFacade.createOrder(orderNumber(), USER_ID, inputs, null));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

@DisplayName("유효한 쿠폰이 있으면, 할인이 적용된 금액으로 주문이 생성되고 쿠폰 상태가 USED로 변경된다.")
        @Test
        void appliesDiscountAndMarksCouponUsed_whenValidCouponProvided() {
            userCoupon = saveFixedCoupon(5000L);
            List<OrderItemInput> inputs = List.of(new OrderItemInput(stock.getId(), 3));

            OrderInfo result = orderFacade.createOrder(orderNumber(), USER_ID, inputs, userCoupon.getId());

            assertThat(result.originalAmount()).isEqualTo(30000L);
            assertThat(result.discountAmount()).isEqualTo(5000L);
            assertThat(result.totalAmount()).isEqualTo(25000L);

            UserCouponStatus status = userCouponRepository.findById(userCoupon.getId()).get().getStatus();
            assertThat(status).isEqualTo(UserCouponStatus.USED);
        }

        @DisplayName("이미 사용된 쿠폰이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponAlreadyUsed() {
            userCoupon = saveFixedCoupon(5000L);
            List<OrderItemInput> inputs = List.of(new OrderItemInput(stock.getId(), 1));
            orderFacade.createOrder(orderNumber(), USER_ID, inputs, userCoupon.getId());

            CoreException exception = assertThrows(CoreException.class,
                    () -> orderFacade.createOrder(orderNumber(), USER_ID, List.of(new OrderItemInput(stock.getId(), 1)), userCoupon.getId()));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("동일한 stockId가 중복되면, 합산된 수량으로 주문이 생성되고 재고가 감소한다.")
        @Test
        void mergesDuplicateInputsAndDecreasesStock() {
            List<OrderItemInput> inputs = List.of(
                    new OrderItemInput(stock.getId(), 2),
                    new OrderItemInput(stock.getId(), 3)
            );

            OrderInfo result = orderFacade.createOrder(orderNumber(), USER_ID, inputs, null);

            assertThat(result.items()).hasSize(1);
            assertThat(result.totalAmount()).isEqualTo(50000L);

            int remainingStock = productStockRepository.findById(stock.getId()).get().getStockQuantity().getValue();
            assertThat(remainingStock).isEqualTo(5);
        }
    }

    @DisplayName("주문 취소(cancelOrder) 시,")
    @Nested
    class CancelOrder {

        @DisplayName("주문이 취소되고, 재고가 복구된다.")
        @Test
        void cancelsOrderAndRestoresStock() {
            OrderInfo order = orderFacade.createOrder(orderNumber(), USER_ID, List.of(new OrderItemInput(stock.getId(), 3)), null);

            OrderInfo result = orderFacade.cancelOrder(order.id(), USER_ID);

            assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED.getDescription());

            int restoredStock = productStockRepository.findById(stock.getId()).get().getStockQuantity().getValue();
            assertThat(restoredStock).isEqualTo(10);
        }

        @DisplayName("쿠폰이 적용된 주문이 취소되면, 재고와 쿠폰이 모두 복구된다.")
        @Test
        void restoresStockAndRevertsCoupon_whenOrderWithCouponIsCancelled() {
            userCoupon = saveFixedCoupon(5000L);
            OrderInfo order = orderFacade.createOrder(orderNumber(), USER_ID, List.of(new OrderItemInput(stock.getId(), 3)), userCoupon.getId());

            OrderInfo result = orderFacade.cancelOrder(order.id(), USER_ID);

            assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED.getDescription());

            int restoredStock = productStockRepository.findById(stock.getId()).get().getStockQuantity().getValue();
            assertThat(restoredStock).isEqualTo(10);

            UserCouponStatus status = userCouponRepository.findById(userCoupon.getId()).get().getStatus();
            assertThat(status).isEqualTo(UserCouponStatus.ISSUED);
        }
    }
}
