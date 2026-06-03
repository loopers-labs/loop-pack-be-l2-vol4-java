package com.loopers.application.ordering.order;

import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.product.ProductSearchCondition;
import com.loopers.domain.catalog.product.StockService;
import com.loopers.domain.ordering.order.Order;
import com.loopers.domain.ordering.order.OrderRepository;
import com.loopers.domain.ordering.order.OrderStatus;
import com.loopers.application.payment.payment.PaymentCommandService;
import com.loopers.domain.payment.payment.Payment;
import com.loopers.domain.payment.payment.PaymentRepository;
import com.loopers.domain.payment.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.domain.DomainEntity;
import com.loopers.support.pagination.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderFacadeTest {

    @DisplayName("주문을 생성할 때, ")
    @Nested
    class PlaceOrder {

        @DisplayName("재고를 차감하고 주문과 REQUESTED 결제를 함께 생성한다.")
        @Test
        void createsOrderAndRequestedPayment_whenItemsAreOrderable() {
            // arrange
            TestFixture fixture = new TestFixture();
            Product product1 = fixture.saveProduct(1L, "상품1", 1_000L, 10);
            Product product2 = fixture.saveProduct(2L, "상품2", 2_000L, 5);

            // act
            OrderResult.Detail result = fixture.facade.placeOrder(new OrderCommand.Create(
                "user1",
                List.of(
                    new OrderCommand.Item(1L, 2),
                    new OrderCommand.Item(2L, 1)
                )
            ));

            // assert
            Payment payment = fixture.paymentRepository.findByOrderId(result.orderId()).orElseThrow();
            assertAll(
                () -> assertThat(result.orderStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING),
                () -> assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.REQUESTED),
                () -> assertThat(result.totalAmount()).isEqualTo(4_000L),
                () -> assertThat(result.items()).hasSize(2),
                () -> assertThat(product1.getStockQuantity()).isEqualTo(8),
                () -> assertThat(product2.getStockQuantity()).isEqualTo(4),
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED),
                () -> assertThat(payment.getAmount()).isEqualTo(4_000L)
            );
        }

        @DisplayName("같은 productId가 중복되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenProductIdIsDuplicated() {
            // arrange
            TestFixture fixture = new TestFixture();
            Product product = fixture.saveProduct(1L, "상품1", 1_000L, 10);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                fixture.facade.placeOrder(new OrderCommand.Create(
                    "user1",
                    List.of(
                        new OrderCommand.Item(1L, 1),
                        new OrderCommand.Item(1L, 2)
                    )
                ));
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(product.getStockQuantity()).isEqualTo(10),
                () -> assertThat(fixture.orderRepository.orders).isEmpty(),
                () -> assertThat(fixture.paymentRepository.payments).isEmpty()
            );
        }

        @DisplayName("일부 상품의 재고가 부족하면 전체 주문을 생성하지 않고 재고를 차감하지 않는다.")
        @Test
        void rejectsWholeOrder_whenAnyItemIsOutOfStock() {
            // arrange
            TestFixture fixture = new TestFixture();
            Product product1 = fixture.saveProduct(1L, "상품1", 1_000L, 10);
            Product product2 = fixture.saveProduct(2L, "상품2", 2_000L, 1);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                fixture.facade.placeOrder(new OrderCommand.Create(
                    "user1",
                    List.of(
                        new OrderCommand.Item(1L, 2),
                        new OrderCommand.Item(2L, 2)
                    )
                ));
            });

            // assert
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(product1.getStockQuantity()).isEqualTo(10),
                () -> assertThat(product2.getStockQuantity()).isEqualTo(1),
                () -> assertThat(fixture.orderRepository.orders).isEmpty(),
                () -> assertThat(fixture.paymentRepository.payments).isEmpty()
            );
        }
    }

    @DisplayName("주문을 조회할 때, ")
    @Nested
    class GetOrder {

        @DisplayName("주문 상세에 non-null 결제 상태를 함께 반환한다.")
        @Test
        void returnsPaymentStatusWithOrderDetail() {
            // arrange
            TestFixture fixture = new TestFixture();
            fixture.saveProduct(1L, "상품1", 1_000L, 10);
            OrderResult.Detail created = fixture.facade.placeOrder(new OrderCommand.Create(
                "user1",
                List.of(new OrderCommand.Item(1L, 2))
            ));

            // act
            OrderResult.Detail result = fixture.queryService.getOrder("user1", created.orderId());

            // assert
            assertAll(
                () -> assertThat(result.orderId()).isEqualTo(created.orderId()),
                () -> assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.REQUESTED),
                () -> assertThat(result.items()).hasSize(1)
            );
        }
    }

    @DisplayName("어드민이 주문을 조회할 때, ")
    @Nested
    class GetAdminOrders {

        @DisplayName("전체 주문 목록을 페이지 조건으로 조회하고 userId와 결제 상태를 함께 반환한다.")
        @Test
        void returnsPagedOrdersWithUserIdAndPaymentStatus() {
            // arrange
            TestFixture fixture = new TestFixture();
            fixture.saveProduct(1L, "상품1", 1_000L, 10);
            fixture.facade.placeOrder(new OrderCommand.Create(
                "user1",
                List.of(new OrderCommand.Item(1L, 1))
            ));
            fixture.facade.placeOrder(new OrderCommand.Create(
                "user2",
                List.of(new OrderCommand.Item(1L, 2))
            ));

            // act
            PageResult<OrderResult.Summary> result = fixture.queryService.getAdminOrders(0, 20);

            // assert
            assertAll(
                () -> assertThat(result.totalElements()).isEqualTo(2L),
                () -> assertThat(result.items()).hasSize(2),
                () -> assertThat(result.items()).extracting(OrderResult.Summary::userId)
                    .containsExactlyInAnyOrder("user1", "user2"),
                () -> assertThat(result.items()).extracting(OrderResult.Summary::paymentStatus)
                    .containsExactlyInAnyOrder(PaymentStatus.REQUESTED, PaymentStatus.REQUESTED)
            );
        }

        @DisplayName("단일 주문 상세를 userId와 함께 조회한다.")
        @Test
        void returnsOrderDetailWithUserId() {
            // arrange
            TestFixture fixture = new TestFixture();
            fixture.saveProduct(1L, "상품1", 1_000L, 10);
            OrderResult.Detail created = fixture.facade.placeOrder(new OrderCommand.Create(
                "user1",
                List.of(new OrderCommand.Item(1L, 2))
            ));

            // act
            OrderResult.Detail result = fixture.queryService.getAdminOrder(created.orderId());

            // assert
            assertAll(
                () -> assertThat(result.orderId()).isEqualTo(created.orderId()),
                () -> assertThat(result.userId()).isEqualTo("user1"),
                () -> assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.REQUESTED),
                () -> assertThat(result.items()).hasSize(1)
            );
        }
    }

    private static class TestFixture {
        private final FakeProductRepository productRepository = new FakeProductRepository();
        private final FakeOrderRepository orderRepository = new FakeOrderRepository();
        private final FakePaymentRepository paymentRepository = new FakePaymentRepository();
        private final OrderFacade facade = new OrderFacade(
            new OrderCommandService(orderRepository, new StockService(productRepository)),
            new PaymentCommandService(paymentRepository)
        );
        private final OrderQueryService queryService = new OrderQueryService(orderRepository, paymentRepository);

        private Product saveProduct(Long id, String name, Long price, Integer stockQuantity) {
            Product product = withId(new Product(1L, name, "설명", price, stockQuantity), id);
            productRepository.products.put(id, product);
            return product;
        }
    }

    private static class FakeProductRepository implements ProductRepository {
        private final Map<Long, Product> products = new HashMap<>();

        @Override
        public Product save(Product product) {
            return product;
        }

        @Override
        public Optional<Product> find(Long id) {
            return Optional.ofNullable(products.get(id));
        }

        @Override
        public Optional<Product> findOnSale(Long id) {
            return find(id).filter(Product::isOnSale);
        }

        @Override
        public List<Product> findAllByIds(Collection<Long> ids) {
            return ids.stream()
                .map(products::get)
                .toList();
        }

        @Override
        public List<Product> findAllByIdsForUpdate(Collection<Long> ids) {
            return findAllByIds(ids).stream()
                .filter(product -> product != null)
                .toList();
        }

        @Override
        public List<Product> findByBrandId(Long brandId) {
            return products.values()
                .stream()
                .filter(product -> product.getBrandId().equals(brandId))
                .toList();
        }

        @Override
        public List<Product> search(ProductSearchCondition condition) {
            return products.values()
                .stream()
                .filter(product -> condition.status() == null || product.getStatus() == condition.status())
                .filter(product -> condition.brandId() == null || product.getBrandId().equals(condition.brandId()))
                .toList();
        }

        @Override
        public long count(ProductSearchCondition condition) {
            return search(condition).size();
        }

        @Override
        public int increaseLikeCount(Long productId) {
            products.get(productId).increaseLikeCount();
            return 1;
        }

        @Override
        public int decreaseLikeCount(Long productId) {
            products.get(productId).decreaseLikeCount();
            return 1;
        }
    }

    private static class FakeOrderRepository implements OrderRepository {
        private final Map<Long, Order> orders = new HashMap<>();
        private long sequence = 1L;

        @Override
        public Order save(Order order) {
            if (order.getId() == 0L) {
                withId(order, sequence++);
            }
            orders.put(order.getId(), order);
            return order;
        }

        @Override
        public Optional<Order> find(Long orderId) {
            return Optional.ofNullable(orders.get(orderId));
        }

        @Override
        public Optional<Order> findByIdAndUserId(Long orderId, String userId) {
            return find(orderId).filter(order -> order.getUserId().equals(userId));
        }

        @Override
        public List<Order> findByUserIdAndCreatedAtBetween(String userId, ZonedDateTime startAt, ZonedDateTime endAt) {
            return orders.values()
                .stream()
                .filter(order -> order.getUserId().equals(userId))
                .toList();
        }

        @Override
        public List<Order> findAllForAdmin(int page, int size) {
            return orders.values()
                .stream()
                .skip((long) page * size)
                .limit(size)
                .toList();
        }

        @Override
        public long countAll() {
            return orders.size();
        }
    }

    private static class FakePaymentRepository implements PaymentRepository {
        private final Map<Long, Payment> payments = new HashMap<>();

        @Override
        public Payment save(Payment payment) {
            payments.put(payment.getOrderId(), payment);
            return payment;
        }

        @Override
        public Optional<Payment> findByOrderId(Long orderId) {
            return Optional.ofNullable(payments.get(orderId));
        }

        @Override
        public List<Payment> findAllByOrderIds(Collection<Long> orderIds) {
            return orderIds.stream()
                .map(payments::get)
                .filter(payment -> payment != null)
                .toList();
        }

        @Override
        public List<Payment> findRequestedPayments() {
            return payments.values()
                .stream()
                .filter(Payment::isRequested)
                .toList();
        }
    }

    private static <T extends DomainEntity> T withId(T entity, Long id) {
        try {
            Field field = DomainEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
