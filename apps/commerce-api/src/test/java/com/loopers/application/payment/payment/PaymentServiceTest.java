package com.loopers.application.payment.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.product.ProductSearchCondition;
import com.loopers.domain.catalog.product.StockService;
import com.loopers.application.event.order.OrderEventPublisher;
import com.loopers.domain.event.outbox.OrderEventOutbox;
import com.loopers.domain.event.outbox.OrderEventOutboxRepository;
import com.loopers.application.ordering.order.OrderCommand;
import com.loopers.application.ordering.order.OrderCommandService;
import com.loopers.domain.ordering.order.Order;
import com.loopers.domain.ordering.order.OrderRepository;
import com.loopers.domain.ordering.order.OrderStatus;
import com.loopers.domain.payment.gateway.PaymentGateway;
import com.loopers.domain.payment.gateway.PaymentGatewayResult;
import com.loopers.domain.payment.payment.Payment;
import com.loopers.domain.payment.payment.PaymentRepository;
import com.loopers.domain.payment.payment.PaymentStatus;
import com.loopers.support.domain.DomainEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PaymentServiceTest {

    @DisplayName("요청된 결제를 처리할 때, ")
    @Nested
    class ProcessRequestedPayment {

        @DisplayName("승인과 매입이 성공하면 결제를 SUCCESS로, 주문을 PAID로 변경하고 재고는 유지한다.")
        @Test
        void marksPaymentSuccessAndOrderPaid_whenAuthorizeAndCaptureSucceed() {
            // arrange
            TestFixture fixture = new TestFixture();
            Product product = fixture.saveProduct(1L, 10);
            Order order = fixture.createOrder(1L, 2);
            Payment payment = fixture.createPayment(order);

            // act
            PaymentProcessResult result = fixture.paymentService.processRequestedPayment(order.getId());

            // assert
            assertAll(
                () -> assertThat(result.status()).isEqualTo(PaymentProcessResult.Status.SUCCESS),
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(payment.getTransactionKey()).isEqualTo("tx-" + order.getId()),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID),
                () -> assertThat(product.getStockQuantity()).isEqualTo(8),
                () -> assertThat(fixture.orderEventOutboxRepository.outboxes).hasSize(1),
                () -> assertThat(fixture.orderEventOutboxRepository.outboxes.get(0).getEventType())
                    .isEqualTo(OrderEventOutbox.ORDER_PAID)
            );
        }

        @DisplayName("승인이 실패하면 결제를 FAILED로, 주문을 PAYMENT_FAILED로 변경하고 재고를 복구한다.")
        @Test
        void marksPaymentFailedAndRestoresStock_whenAuthorizeFails() {
            // arrange
            TestFixture fixture = new TestFixture();
            fixture.gateway.authorizeResult = PaymentGatewayResult.failed("승인 실패");
            Product product = fixture.saveProduct(1L, 10);
            Order order = fixture.createOrder(1L, 2);
            Payment payment = fixture.createPayment(order);

            // act
            PaymentProcessResult result = fixture.paymentService.processRequestedPayment(order.getId());

            // assert
            assertAll(
                () -> assertThat(result.status()).isEqualTo(PaymentProcessResult.Status.FAILED),
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getFailureReason()).isEqualTo("승인 실패"),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED),
                () -> assertThat(product.getStockQuantity()).isEqualTo(10),
                () -> assertThat(fixture.orderEventOutboxRepository.outboxes).isEmpty()
            );
        }

        @DisplayName("매입이 실패하면 승인을 취소하고 결제를 FAILED로, 주문을 PAYMENT_FAILED로 변경하며 재고를 복구한다.")
        @Test
        void voidsAuthorizationAndRestoresStock_whenCaptureFails() {
            // arrange
            TestFixture fixture = new TestFixture();
            fixture.gateway.captureResult = PaymentGatewayResult.failed("매입 실패");
            Product product = fixture.saveProduct(1L, 10);
            Order order = fixture.createOrder(1L, 2);
            Payment payment = fixture.createPayment(order);

            // act
            PaymentProcessResult result = fixture.paymentService.processRequestedPayment(order.getId());

            // assert
            assertAll(
                () -> assertThat(result.status()).isEqualTo(PaymentProcessResult.Status.FAILED),
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED),
                () -> assertThat(product.getStockQuantity()).isEqualTo(10),
                () -> assertThat(fixture.gateway.voidAuthorizationCallCount).isEqualTo(1)
            );
        }

        @DisplayName("주문 생성 후 1분이 지나면 결제를 TIMEOUT 실패로 처리하고 재고를 복구한다.")
        @Test
        void expiresPaymentAndRestoresStock_whenOrderIsOlderThanOneMinute() {
            // arrange
            TestFixture fixture = new TestFixture();
            Product product = fixture.saveProduct(1L, 10);
            Order order = fixture.createOrder(1L, 2);
            withCreatedAt(order, ZonedDateTime.now().minusMinutes(2));
            Payment payment = fixture.createPayment(order);

            // act
            PaymentProcessResult result = fixture.paymentService.processRequestedPayment(order.getId());

            // assert
            assertAll(
                () -> assertThat(result.status()).isEqualTo(PaymentProcessResult.Status.EXPIRED),
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getFailureReason()).isEqualTo("TIMEOUT"),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED),
                () -> assertThat(product.getStockQuantity()).isEqualTo(10),
                () -> assertThat(fixture.gateway.authorizeCallCount).isZero()
            );
        }

        @DisplayName("외부 매입 성공 후 내부 성공 반영 시점에 1분이 지나도 결제 성공을 우선 반영한다.")
        @Test
        void marksSuccess_whenCapturedPaymentIsReportedAfterOneMinute() {
            // arrange
            TestFixture fixture = new TestFixture();
            Product product = fixture.saveProduct(1L, 10);
            Order order = fixture.createOrder(1L, 2);
            withCreatedAt(order, ZonedDateTime.now().minusMinutes(2));
            Payment payment = fixture.createPayment(order);

            // act
            PaymentProcessResult result = fixture.paymentResultService.markSuccess(order.getId(), "tx-" + order.getId());

            // assert
            assertAll(
                () -> assertThat(result.status()).isEqualTo(PaymentProcessResult.Status.SUCCESS),
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID),
                () -> assertThat(product.getStockQuantity()).isEqualTo(8),
                () -> assertThat(fixture.orderEventOutboxRepository.outboxes).hasSize(1)
            );
        }
    }

    private static class TestFixture {
        private final FakeProductRepository productRepository = new FakeProductRepository();
        private final FakeOrderRepository orderRepository = new FakeOrderRepository();
        private final FakePaymentRepository paymentRepository = new FakePaymentRepository();
        private final FakeOrderEventOutboxRepository orderEventOutboxRepository = new FakeOrderEventOutboxRepository();
        private final FakePaymentGateway gateway = new FakePaymentGateway();
        private final OrderCommandService orderCommandService = new OrderCommandService(
            orderRepository,
            new StockService(productRepository)
        );
        private final OrderEventPublisher orderEventPublisher = new OrderEventPublisher(orderEventOutboxRepository, objectMapper());
        private final PaymentResultService paymentResultService = new PaymentResultService(
            paymentRepository,
            orderCommandService,
            orderEventPublisher
        );
        private final PaymentService paymentService = new PaymentService(
            paymentRepository,
            orderRepository,
            gateway,
            paymentResultService
        );

        private Product saveProduct(Long id, Integer stockQuantity) {
            Product product = withId(new Product(1L, "상품" + id, "설명", 1_000L, stockQuantity), id);
            productRepository.products.put(id, product);
            return product;
        }

        private Order createOrder(Long productId, Integer quantity) {
            return orderCommandService.createPendingOrder(new OrderCommand.Create(
                "user1",
                List.of(new OrderCommand.Item(productId, quantity))
            ));
        }

        private Payment createPayment(Order order) {
            Payment payment = new Payment(order.getId(), order.getTotalAmount());
            paymentRepository.save(payment);
            return payment;
        }
    }

    private static class FakeOrderEventOutboxRepository implements OrderEventOutboxRepository {
        private final List<OrderEventOutbox> outboxes = new ArrayList<>();

        @Override
        public OrderEventOutbox save(OrderEventOutbox outbox) {
            outboxes.add(outbox);
            return outbox;
        }

        @Override
        public List<OrderEventOutbox> findPendingEvents() {
            return outboxes.stream()
                .filter(OrderEventOutbox::isPending)
                .toList();
        }
    }

    private static class FakePaymentGateway implements PaymentGateway {
        private PaymentGatewayResult authorizeResult;
        private PaymentGatewayResult captureResult = PaymentGatewayResult.success("captured");
        private int authorizeCallCount = 0;
        private int voidAuthorizationCallCount = 0;

        @Override
        public PaymentGatewayResult authorize(Long orderId, Long amount, String idempotencyKey) {
            authorizeCallCount++;
            if (authorizeResult != null) {
                return authorizeResult;
            }

            return PaymentGatewayResult.success("tx-" + orderId);
        }

        @Override
        public PaymentGatewayResult capture(String transactionKey) {
            return captureResult;
        }

        @Override
        public PaymentGatewayResult voidAuthorization(String transactionKey) {
            voidAuthorizationCallCount++;
            return PaymentGatewayResult.success(transactionKey);
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
        public Optional<Order> findForUpdate(Long orderId) {
            return find(orderId);
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

    private static void withCreatedAt(DomainEntity entity, ZonedDateTime createdAt) {
        try {
            Field field = DomainEntity.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(entity, createdAt);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
