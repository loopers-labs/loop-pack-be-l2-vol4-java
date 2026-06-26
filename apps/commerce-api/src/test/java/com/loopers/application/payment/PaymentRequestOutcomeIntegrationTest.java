package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderItems;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRequestException;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.Money;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제 요청 결과 처리({@link PaymentApplicationService#pay}) — PG 실패의 in-doubt 여부에 따른 분기 검증.
 *
 * <p>응용은 {@link PaymentRequestException#isInDoubt()} 한 비트만 보고 가른다:
 * <ul>
 *   <li><b>in-doubt</b>(read/connect 타임아웃·5xx): 청구 여부가 "모름"이라 함부로 단정하지 않고
 *       {@code PENDING} 을 유지한다 → 콜백/정산으로 확정.</li>
 *   <li><b>not-in-doubt</b>(4xx 거절·CB-open·연결 거부): 청구가 일어나지 않음이 확실 → PENDING 으로 숨기지 않고
 *       {@code FAILED} 로 확정한다(좀비 PENDING 방지, 가드가 정정 후 재시도 허용).</li>
 * </ul>
 *
 * <p>어떤 저수준 예외가 in-doubt 인지의 분류는 어댑터 단위테스트에서 검증한다.</p>
 */
@DisplayName("결제 요청 결과 처리 — PG 실패 유형별 분기")
@SpringBootTest
class PaymentRequestOutcomeIntegrationTest {

    private static final Long USER_ID = 7L;
    private static final long AMOUNT = 5_000L;

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private ConfigurablePgGateway pgGateway;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long orderId;

    @BeforeEach
    void setUp() {
        Order order = orderJpaRepository.save(Order.create(
                USER_ID,
                OrderItems.from(List.of(OrderItem.of(1L, "상품", Money.of(AMOUNT), 1))),
                Money.of(0L)));
        orderId = order.getId();
        pgGateway.reset();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("PG 실패가 not-in-doubt(청구 없음 확실)면 결제는 FAILED 로 확정되고 주문은 CREATED 로 남는다.")
    @Test
    void notInDoubtFailure_marksFailed() {
        pgGateway.failWith(PaymentRequestException.notInDoubt("PG 가 결제 요청을 거절했습니다. (status=400)"));

        PaymentInfo.Requested info = paymentApplicationService.pay(new PaymentCriteria.Pay(
                USER_ID, orderId, CardType.SAMSUNG, "1234-5678-9814-1451"));

        assertThat(info.status()).isEqualTo(PaymentStatus.FAILED);
        Payment saved = paymentJpaRepository.findAllByOrderId(orderId).get(0);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.getReason()).contains("거절");
        assertThat(saved.getTransactionKey()).as("접수 전이라 키가 없다").isNull();
        assertThat(orderJpaRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @DisplayName("PG 실패가 in-doubt(청구 여부 모름)면 결제는 PENDING 으로 유지된다(콜백/정산 대상).")
    @Test
    void inDoubtFailure_keepsPending() {
        pgGateway.failWith(PaymentRequestException.inDoubt("결제 시스템이 일시적으로 응답하지 않습니다."));

        PaymentInfo.Requested info = paymentApplicationService.pay(new PaymentCriteria.Pay(
                USER_ID, orderId, CardType.SAMSUNG, "1234-5678-9814-1451"));

        assertThat(info.status()).isEqualTo(PaymentStatus.PENDING);
        Payment saved = paymentJpaRepository.findAllByOrderId(orderId).get(0);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getTransactionKey()).as("접수 못 함 → 키 없음").isNull();
    }

    @DisplayName("PG 접수 성공이면 PENDING 을 유지하고 transactionKey 를 보관한다.")
    @Test
    void accepted_keepsPendingWithTransactionKey() {
        pgGateway.succeedWith("TR-OK-1");

        PaymentInfo.Requested info = paymentApplicationService.pay(new PaymentCriteria.Pay(
                USER_ID, orderId, CardType.SAMSUNG, "1234-5678-9814-1451"));

        assertThat(info.status()).isEqualTo(PaymentStatus.PENDING);
        Payment saved = paymentJpaRepository.findAllByOrderId(orderId).get(0);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getTransactionKey()).isEqualTo("TR-OK-1");
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        ConfigurablePgGateway configurablePgGateway() {
            return new ConfigurablePgGateway();
        }
    }

    /** request() 를 테스트가 지정한 결과(성공 키 반환 또는 예외)로 돌려주는 PG 스텁. */
    static class ConfigurablePgGateway implements PaymentGateway {

        private volatile RuntimeException failure;
        private volatile String transactionKey;

        void reset() {
            failure = null;
            transactionKey = null;
        }

        void failWith(RuntimeException e) {
            this.failure = e;
            this.transactionKey = null;
        }

        void succeedWith(String transactionKey) {
            this.failure = null;
            this.transactionKey = transactionKey;
        }

        @Override
        public Result request(Command command) {
            if (failure != null) {
                throw failure;
            }
            return new Result(transactionKey, PaymentStatus.PENDING, null);
        }

        @Override
        public Optional<Result> find(String transactionKey, String userId) {
            return Optional.empty();
        }
    }
}
