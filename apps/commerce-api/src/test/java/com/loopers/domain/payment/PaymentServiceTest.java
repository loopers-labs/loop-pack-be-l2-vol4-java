package com.loopers.domain.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PaymentServiceTest {

    private static final String CARD_NO = "1234-5678-9814-1451";
    private static final String MASKED = "1234-****-****-1451";

    private FakePaymentRepository repository;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        repository = new FakePaymentRepository();
        paymentService = new PaymentService(repository);
    }

    private PaymentModel savePendingWithKey(Long orderId, String key) {
        PaymentModel payment = paymentService.createPending(orderId, "ORD" + orderId, 10L, CardType.SAMSUNG, CARD_NO, 5000L);
        if (key != null) {
            paymentService.attachTransactionKey(payment.getId(), key);
        }
        return payment;
    }

    @DisplayName("결제를 접수(createPending)할 때,")
    @Nested
    class CreatePending {

        @DisplayName("PENDING 결제가 저장되고 카드 번호는 마스킹된다.")
        @Test
        void savesPendingPayment() {
            // when
            PaymentModel payment = paymentService.createPending(
                    1L, "ORD1", 10L, CardType.SAMSUNG, CARD_NO, 5000L);

            // then
            assertAll(
                    () -> assertThat(payment.getId()).isNotNull(),
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(payment.getOrderId()).isEqualTo(1L),
                    () -> assertThat(payment.getAmount()).isEqualTo(5000L),
                    () -> assertThat(payment.getCardNo()).isEqualTo(MASKED)
            );
        }
    }

    @DisplayName("접수 응답/격리/폴링 조회를 할 때,")
    @Nested
    class AttachAndQuery {

        @DisplayName("attachTransactionKey 후에도 PENDING 이 유지되고 키가 저장된다.")
        @Test
        void attachesKey_keepingPending() {
            // given
            PaymentModel payment = paymentService.createPending(1L, "ORD1", 10L, CardType.SAMSUNG, CARD_NO, 5000L);

            // when
            paymentService.attachTransactionKey(payment.getId(), "20260626:TR:abc");

            // then
            PaymentModel found = repository.findById(payment.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(found.getStatus()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(found.getTransactionKey()).isEqualTo("20260626:TR:abc")
            );
        }

        @DisplayName("markUnknown 은 PENDING 결제를 UNKNOWN 으로 격리한다.")
        @Test
        void isolatesPendingToUnknown() {
            // given
            PaymentModel payment = paymentService.createPending(1L, "ORD1", 10L, CardType.SAMSUNG, CARD_NO, 5000L);

            // when
            paymentService.markUnknown(payment.getId());

            // then
            assertThat(repository.findById(payment.getId()).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.UNKNOWN);
        }

        @DisplayName("findPendingForReconcile 은 threshold 이전 PENDING 만 반환한다.")
        @Test
        void returnsPendingOlderThanThreshold() {
            // given
            PaymentModel old = paymentService.createPending(1L, "ORD1", 10L, CardType.SAMSUNG, CARD_NO, 5000L);
            PaymentModel fresh = paymentService.createPending(2L, "ORD2", 10L, CardType.SAMSUNG, CARD_NO, 5000L);
            ReflectionTestUtils.setField(old, "createdAt", ZonedDateTime.now().minusMinutes(1));
            ReflectionTestUtils.setField(fresh, "createdAt", ZonedDateTime.now().plusMinutes(1));

            // when
            List<PaymentModel> result = paymentService.findPendingForReconcile(ZonedDateTime.now());

            // then
            assertThat(result).extracting(PaymentModel::getId).containsExactly(old.getId());
        }
    }

    @DisplayName("결과를 확정(confirm)할 때,")
    @Nested
    class Confirm {

        @DisplayName("무결성 일치 + PENDING 이면 PAID 로 전이하고 PAID 후처리 신호를 반환한다.")
        @Test
        void transitionsToPaid_whenMatchAndPending() {
            // given
            PaymentModel payment = savePendingWithKey(1L, "20260626:TR:abc");

            // when
            ConfirmOutcome outcome = paymentService.confirm(
                    "20260626:TR:abc", PaymentStatus.PAID, "정상 승인", 5000L, CARD_NO);

            // then
            assertAll(
                    () -> assertThat(outcome.result()).isEqualTo(ConfirmOutcome.Result.PAID),
                    () -> assertThat(outcome.orderId()).isEqualTo(1L),
                    () -> assertThat(repository.findById(payment.getId()).orElseThrow().getStatus())
                            .isEqualTo(PaymentStatus.PAID)
            );
        }

        @DisplayName("이미 전이된 결제(affected=0)면 후처리를 스킵한다(멱등).")
        @Test
        void skipsPostProcessing_whenAlreadyTransitioned() {
            // given
            PaymentModel payment = savePendingWithKey(1L, "20260626:TR:abc");
            repository.transitionToPaid(payment.getId(), "20260626:TR:abc"); // 이미 누군가 전이시킴

            // when
            ConfirmOutcome outcome = paymentService.confirm(
                    "20260626:TR:abc", PaymentStatus.PAID, "정상 승인", 5000L, CARD_NO);

            // then
            assertThat(outcome.result()).isEqualTo(ConfirmOutcome.Result.SKIPPED);
        }

        @DisplayName("amount 가 불일치하면 전이를 거부하고 UNKNOWN 으로 격리한다.")
        @Test
        void isolatesToUnknown_whenAmountMismatch() {
            // given
            PaymentModel payment = savePendingWithKey(1L, "20260626:TR:abc");

            // when
            ConfirmOutcome outcome = paymentService.confirm(
                    "20260626:TR:abc", PaymentStatus.PAID, "정상 승인", 9999L, CARD_NO);

            // then
            assertAll(
                    () -> assertThat(outcome.result()).isEqualTo(ConfirmOutcome.Result.ISOLATED),
                    () -> assertThat(repository.findById(payment.getId()).orElseThrow().getStatus())
                            .isEqualTo(PaymentStatus.UNKNOWN)
            );
        }

        @DisplayName("cardNo 가 불일치하면 전이를 거부하고 UNKNOWN 으로 격리한다.")
        @Test
        void isolatesToUnknown_whenCardNoMismatch() {
            // given
            PaymentModel payment = savePendingWithKey(1L, "20260626:TR:abc");

            // when
            ConfirmOutcome outcome = paymentService.confirm(
                    "20260626:TR:abc", PaymentStatus.PAID, "정상 승인", 5000L, "9999-8888-7777-6666");

            // then
            assertAll(
                    () -> assertThat(outcome.result()).isEqualTo(ConfirmOutcome.Result.ISOLATED),
                    () -> assertThat(repository.findById(payment.getId()).orElseThrow().getStatus())
                            .isEqualTo(PaymentStatus.UNKNOWN)
            );
        }
    }

    /** 단위 테스트용 in-memory Fake (write-then-read 케이스, CLAUDE.md 허용). */
    static class FakePaymentRepository implements PaymentRepository {
        private final Map<Long, PaymentModel> store = new HashMap<>();
        private long seq = 0;

        @Override
        public PaymentModel save(PaymentModel payment) {
            if (payment.getId() == null) {
                seq++;
                ReflectionTestUtils.setField(payment, "id", seq);
                if (payment.getCreatedAt() == null) {
                    ReflectionTestUtils.setField(payment, "createdAt", ZonedDateTime.now());
                }
            }
            store.put(payment.getId(), payment);
            return payment;
        }

        @Override
        public Optional<PaymentModel> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<PaymentModel> findByTransactionKey(String transactionKey) {
            return store.values().stream()
                    .filter(p -> transactionKey.equals(p.getTransactionKey()))
                    .findFirst();
        }

        @Override
        public Optional<PaymentModel> findActiveByOrderId(Long orderId) {
            return store.values().stream()
                    .filter(p -> orderId.equals(p.getOrderId())
                            && (p.getStatus() == PaymentStatus.PENDING || p.getStatus() == PaymentStatus.PAID))
                    .findFirst();
        }

        @Override
        public int transitionToPaid(Long id, String transactionKey) {
            PaymentModel p = store.get(id);
            if (p != null && p.getStatus() == PaymentStatus.PENDING) {
                p.markPaid(transactionKey);
                return 1;
            }
            return 0;
        }

        @Override
        public int transitionToFailed(Long id, String reason) {
            PaymentModel p = store.get(id);
            if (p != null && p.getStatus() == PaymentStatus.PENDING) {
                p.markFailed(reason);
                return 1;
            }
            return 0;
        }

        @Override
        public int transitionToUnknown(Long id) {
            PaymentModel p = store.get(id);
            if (p != null && p.getStatus() == PaymentStatus.PENDING) {
                p.markUnknown();
                return 1;
            }
            return 0;
        }

        @Override
        public List<PaymentModel> findPendingOlderThan(ZonedDateTime threshold) {
            return store.values().stream()
                    .filter(p -> p.getStatus() == PaymentStatus.PENDING
                            && p.getCreatedAt() != null && p.getCreatedAt().isBefore(threshold))
                    .toList();
        }
    }
}
