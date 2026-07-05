package com.loopers.application.payment.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loopers.application.event.order.OrderEventPublisher;
import com.loopers.application.ordering.order.OrderCommandService;
import com.loopers.domain.event.outbox.EventOutbox;
import com.loopers.domain.event.outbox.EventOutboxRepository;
import com.loopers.domain.ordering.order.Order;
import com.loopers.domain.ordering.order.OrderLine;
import com.loopers.domain.ordering.order.OrderStatus;
import com.loopers.domain.ordering.order.OrderRepository;
import com.loopers.domain.payment.gateway.PaymentGateway;
import com.loopers.domain.payment.gateway.PaymentGatewayCommand;
import com.loopers.domain.payment.gateway.PaymentGatewayResult;
import com.loopers.domain.payment.payment.Payment;
import com.loopers.domain.payment.payment.PaymentRepository;
import com.loopers.domain.payment.payment.PaymentStatus;
import com.loopers.support.domain.DomainEntity;
import org.junit.jupiter.api.DisplayName;
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

class PaymentFacadeTest {

    @DisplayName("결제 요청 API는 PG PENDING 응답을 내부 PROCESSING 상태로 반영한다.")
    @Test
    void marksPaymentProcessing_whenPgAcceptsPaymentRequest() {
        // arrange
        TestFixture fixture = new TestFixture();
        Order order = fixture.saveOrder("user1", 2_000L);
        Payment payment = fixture.savePayment(order);
        fixture.gateway.result = PaymentGatewayResult.pending("20250816:TR:9577c5");

        // act
        PaymentResult.Request result = fixture.paymentFacade.requestPayment(new PaymentCommand.Request(
            "user1",
            order.getId(),
            PaymentCommand.CardType.SAMSUNG,
            "1234-5678-9814-1451"
        ));

        // assert
        assertAll(
            () -> assertThat(result.orderId()).isEqualTo(order.getId()),
            () -> assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.PROCESSING),
            () -> assertThat(result.transactionKey()).isEqualTo("20250816:TR:9577c5"),
            () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING),
            () -> assertThat(payment.getTransactionKey()).isEqualTo("20250816:TR:9577c5"),
            () -> assertThat(fixture.gateway.command.orderId()).isEqualTo(String.valueOf(order.getId())),
            () -> assertThat(fixture.gateway.command.amount()).isEqualTo(2_000L)
        );
    }

    @DisplayName("PG 요청 시 내부 주문 ID가 6자리 미만이면 0으로 채워 보낸다.")
    @Test
    void padsOrderId_whenRequestingPgPayment() {
        // arrange
        TestFixture fixture = new TestFixture();
        Order order = fixture.saveOrder("user1", 2_000L, 1L);
        fixture.savePayment(order);
        fixture.gateway.result = PaymentGatewayResult.pending("20250816:TR:9577c5");

        // act
        fixture.paymentFacade.requestPayment(new PaymentCommand.Request(
            "user1",
            order.getId(),
            PaymentCommand.CardType.SAMSUNG,
            "1234-5678-9814-1451"
        ));

        // assert
        assertThat(fixture.gateway.command.orderId()).isEqualTo("000001");
    }

    @DisplayName("PG 성공 콜백은 내부 결제를 SUCCESS로, 주문을 PAID로 반영한다.")
    @Test
    void marksPaymentSuccessAndOrderPaid_whenSuccessCallbackArrives() {
        // arrange
        TestFixture fixture = new TestFixture();
        Order order = fixture.saveOrder("user1", 2_000L);
        Payment payment = fixture.savePayment(order);
        payment.markProcessing("20250816:TR:9577c5");
        fixture.paymentRepository.save(payment);

        // act
        PaymentResult.Request result = fixture.paymentFacade.handleCallback(new PaymentCommand.Callback(
            "20250816:TR:9577c5",
            String.format("%06d", order.getId()),
            PaymentCommand.CardType.SAMSUNG,
            "1234-5678-9814-1451",
            2_000L,
            PaymentCommand.TransactionStatus.SUCCESS,
            null
        ));

        // assert
        assertAll(
            () -> assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS),
            () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
            () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID),
            () -> assertThat(fixture.orderEventOutboxRepository.outboxes).hasSize(1),
            () -> assertThat(fixture.orderEventOutboxRepository.outboxes.get(0).getEventType())
                .isEqualTo(EventOutbox.EVENT_ORDER_PAID)
        );
    }

    @DisplayName("PG 성공 콜백이 중복으로 도착해도 주문 완료 이벤트는 한 번만 저장한다.")
    @Test
    void doesNotDuplicateSuccess_whenSuccessCallbackArrivesTwice() {
        // arrange
        TestFixture fixture = new TestFixture();
        Order order = fixture.saveOrder("user1", 2_000L);
        Payment payment = fixture.savePayment(order);
        payment.markProcessing("20250816:TR:9577c5");
        fixture.paymentRepository.save(payment);
        PaymentCommand.Callback callback = new PaymentCommand.Callback(
            "20250816:TR:9577c5",
            String.format("%06d", order.getId()),
            PaymentCommand.CardType.SAMSUNG,
            "1234-5678-9814-1451",
            2_000L,
            PaymentCommand.TransactionStatus.SUCCESS,
            null
        );

        // act
        fixture.paymentFacade.handleCallback(callback);
        PaymentResult.Request secondResult = fixture.paymentFacade.handleCallback(callback);

        // assert
        assertAll(
            () -> assertThat(secondResult.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS),
            () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
            () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID),
            () -> assertThat(fixture.orderEventOutboxRepository.outboxes).hasSize(1)
        );
    }

    @DisplayName("콜백이 누락되어도 transactionKey 상태 조회로 결제 성공을 복구한다.")
    @Test
    void syncsPaymentByTransactionKey_whenCallbackIsMissing() {
        // arrange
        TestFixture fixture = new TestFixture();
        Order order = fixture.saveOrder("user1", 2_000L);
        Payment payment = fixture.savePayment(order);
        payment.markProcessing("20250816:TR:9577c5");
        fixture.paymentRepository.save(payment);
        fixture.gateway.getPaymentResult = PaymentGatewayResult.success(
            "20250816:TR:9577c5",
            String.format("%06d", order.getId())
        );

        // act
        PaymentResult.Request result = fixture.paymentFacade.syncPayment("user1", order.getId());

        // assert
        assertAll(
            () -> assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS),
            () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
            () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID),
            () -> assertThat(fixture.gateway.getPaymentUserId).isEqualTo("user1"),
            () -> assertThat(fixture.gateway.getPaymentTransactionKey).isEqualTo("20250816:TR:9577c5")
        );
    }

    private static class TestFixture {
        private final FakeOrderRepository orderRepository = new FakeOrderRepository();
        private final FakePaymentRepository paymentRepository = new FakePaymentRepository();
        private final FakeOrderEventOutboxRepository orderEventOutboxRepository = new FakeOrderEventOutboxRepository();
        private final FakePaymentGateway gateway = new FakePaymentGateway();
        private final PaymentCommandService paymentCommandService = new PaymentCommandService(paymentRepository);
        private final OrderCommandService orderCommandService = new OrderCommandService(orderRepository, null, null);
        private final OrderEventPublisher orderEventPublisher = new OrderEventPublisher(orderEventOutboxRepository, objectMapper());
        private final PaymentResultService paymentResultService = new PaymentResultService(
            paymentRepository,
            orderCommandService,
            orderEventPublisher
        );
        private final PaymentFacade paymentFacade = new PaymentFacade(
            paymentCommandService,
            orderRepository,
            paymentRepository,
            gateway,
            paymentResultService
        );

        private Order saveOrder(String userId, Long amount) {
            return saveOrder(userId, amount, 1351039135L);
        }

        private Order saveOrder(String userId, Long amount, Long id) {
            Order order = new Order(userId, List.of(new OrderLine(1L, "상품", amount, 1)));
            withId(order, id);
            orderRepository.save(order);
            return order;
        }

        private Payment savePayment(Order order) {
            Payment payment = new Payment(order.getId(), order.getFinalAmount());
            paymentRepository.save(payment);
            return payment;
        }
    }

    private static class FakeOrderEventOutboxRepository implements EventOutboxRepository {
        private final List<EventOutbox> outboxes = new java.util.ArrayList<>();

        @Override
        public EventOutbox save(EventOutbox outbox) {
            outboxes.add(outbox);
            return outbox;
        }

        @Override
        public List<EventOutbox> findPendingEvents(int limit) {
            return outboxes.stream()
                .filter(EventOutbox::isPending)
                .toList();
        }
    }

    private static class FakePaymentGateway implements PaymentGateway {
        private PaymentGatewayResult result;
        private PaymentGatewayResult getPaymentResult;
        private PaymentGatewayCommand.Request command;
        private String getPaymentUserId;
        private String getPaymentTransactionKey;

        @Override
        public PaymentGatewayResult requestPayment(PaymentGatewayCommand.Request command) {
            this.command = command;
            return result;
        }

        @Override
        public Optional<PaymentGatewayResult> getPayment(String userId, String transactionKey) {
            this.getPaymentUserId = userId;
            this.getPaymentTransactionKey = transactionKey;
            return Optional.ofNullable(getPaymentResult);
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
        public Optional<Payment> findByTransactionKey(String transactionKey) {
            return payments.values()
                .stream()
                .filter(payment -> transactionKey.equals(payment.getTransactionKey()))
                .findFirst();
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

    private static class FakeOrderRepository implements OrderRepository {
        private final Map<Long, Order> orders = new HashMap<>();

        @Override
        public Order save(Order order) {
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
            return orders.values().stream().toList();
        }

        @Override
        public long countAll() {
            return orders.size();
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

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
