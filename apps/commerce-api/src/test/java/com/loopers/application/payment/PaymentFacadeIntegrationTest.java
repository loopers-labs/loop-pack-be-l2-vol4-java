package com.loopers.application.payment;

import com.loopers.domain.order.OrderItemData;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayException;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.domain.payment.PgTransaction;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class PaymentFacadeIntegrationTest {

    private static final String LOGIN_ID = "user01";
    private static final String LOGIN_PW = "Password1!";
    private static final String CARD_NO = "1234-5678-9814-1451";

    @TestConfiguration
    static class FakeGatewayConfig {
        @Bean
        @Primary
        ConfigurableFakeGateway configurableFakeGateway() {
            return new ConfigurableFakeGateway();
        }
    }

    @Autowired
    private PaymentFacade paymentFacade;
    @Autowired
    private ConfigurableFakeGateway fakeGateway;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncryptor passwordEncryptor;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private RedisCleanUp redisCleanUp;

    @BeforeEach
    void resetGateway() {
        fakeGateway.reset();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private UserModel saveUser() {
        return userRepository.save(new UserModel(LOGIN_ID, LOGIN_PW, "홍길동", "1990-01-01",
                "user@example.com", Gender.MALE, passwordEncryptor));
    }

    private OrderModel saveOrder(Long userId) {
        return orderRepository.save(OrderModel.create(userId,
                List.of(new OrderItemData(1L, "상품", BigDecimal.valueOf(5000), 1L)), BigDecimal.ZERO));
    }

    @DisplayName("결제를 요청할 때,")
    @Nested
    class Pay {

        @DisplayName("PG 접수가 정상이면 transactionKey 가 저장되고 PENDING 으로 남는다.")
        @Test
        void storesTransactionKey_whenAccepted() {
            // given
            UserModel user = saveUser();
            OrderModel order = saveOrder(user.getId());
            fakeGateway.onRequest = () -> new PgTransaction("20260626:TR:abc", PaymentStatus.PENDING, null, null);

            // when
            PaymentInfo info = paymentFacade.pay(LOGIN_ID, LOGIN_PW, order.getOrderNumber(), CardType.SAMSUNG, CARD_NO);

            // then
            PaymentModel saved = paymentRepository.findById(info.paymentId()).orElseThrow();
            assertAll(
                    () -> assertThat(info.status()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(saved.getTransactionKey()).isEqualTo("20260626:TR:abc"),
                    () -> assertThat(saved.getAmount()).isEqualTo(5000L)
            );
        }

        @DisplayName("PG 미도달(PaymentGatewayException)이면 Payment 가 PENDING·transactionKey=null 로 잔류한다.")
        @Test
        void leavesPending_whenGatewayFails() {
            // given
            UserModel user = saveUser();
            OrderModel order = saveOrder(user.getId());
            fakeGateway.onRequest = () -> {
                throw new PaymentGatewayException("미도달(5xx)");
            };

            // when
            PaymentInfo info = paymentFacade.pay(LOGIN_ID, LOGIN_PW, order.getOrderNumber(), CardType.SAMSUNG, CARD_NO);

            // then
            PaymentModel saved = paymentRepository.findById(info.paymentId()).orElseThrow();
            assertAll(
                    () -> assertThat(info.status()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(saved.getTransactionKey()).isNull()
            );
        }

        @DisplayName("FAILED 된 주문을 재결제하면 새 결제 row 가 생성되고 PG 가 다시 호출된다.")
        @Test
        void createsNewRow_whenRepayingFailedOrder() {
            // given — 이전 결제가 FAILED 로 확정된 상태
            UserModel user = saveUser();
            OrderModel order = saveOrder(user.getId());
            fakeGateway.onRequest = () -> new PgTransaction("20260626:TR:new", PaymentStatus.PENDING, null, null);
            PaymentModel failed = paymentRepository.save(PaymentModel.pending(
                    order.getId(), order.getOrderNumber(), user.getId(), CardType.SAMSUNG, CARD_NO, 5000L));
            paymentRepository.transitionToFailed(failed.getId(), "잘못된 카드");

            // when — 카드 바꿔 재결제
            PaymentInfo info = paymentFacade.pay(LOGIN_ID, LOGIN_PW, order.getOrderNumber(), CardType.KB, CARD_NO);

            // then — 새 row 생성 + PG 재호출
            assertAll(
                    () -> assertThat(info.paymentId()).isNotEqualTo(failed.getId()),
                    () -> assertThat(info.status()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(fakeGateway.requestCount.get()).isEqualTo(1),
                    () -> assertThat(paymentRepository.findById(failed.getId()).orElseThrow().getStatus())
                            .isEqualTo(PaymentStatus.FAILED)
            );
        }

        @DisplayName("활성 결제가 이미 있으면 PG 를 재호출하지 않고 멱등 반환한다(따닥 클릭 방어).")
        @Test
        void returnsIdempotently_whenActivePaymentExists() {
            // given
            UserModel user = saveUser();
            OrderModel order = saveOrder(user.getId());
            fakeGateway.onRequest = () -> new PgTransaction("20260626:TR:abc", PaymentStatus.PENDING, null, null);

            // when
            PaymentInfo first = paymentFacade.pay(LOGIN_ID, LOGIN_PW, order.getOrderNumber(), CardType.SAMSUNG, CARD_NO);
            PaymentInfo second = paymentFacade.pay(LOGIN_ID, LOGIN_PW, order.getOrderNumber(), CardType.SAMSUNG, CARD_NO);

            // then
            assertAll(
                    () -> assertThat(second.paymentId()).isEqualTo(first.paymentId()),
                    () -> assertThat(fakeGateway.requestCount.get()).isEqualTo(1)
            );
        }
    }

    /** 시나리오별로 동작을 주입할 수 있는 PaymentGateway Fake. */
    static class ConfigurableFakeGateway implements PaymentGateway {
        final AtomicInteger requestCount = new AtomicInteger(0);
        Supplier<PgTransaction> onRequest = () -> new PgTransaction("20260626:TR:default", PaymentStatus.PENDING, null, null);

        void reset() {
            requestCount.set(0);
            onRequest = () -> new PgTransaction("20260626:TR:default", PaymentStatus.PENDING, null, null);
        }

        @Override
        public PgTransaction request(PgPaymentCommand command) {
            requestCount.incrementAndGet();
            return onRequest.get();
        }

        @Override
        public Optional<PgTransaction> findByTransactionKey(String transactionKey) {
            return Optional.empty();
        }

        @Override
        public List<PgTransaction> findByOrderId(String orderNumber) {
            return List.of();
        }
    }
}
