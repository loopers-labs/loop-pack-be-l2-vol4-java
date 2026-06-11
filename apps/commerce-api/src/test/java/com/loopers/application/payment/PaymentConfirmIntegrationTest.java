package com.loopers.application.payment;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderTransactionService;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 결제 승인(confirm) 유스케이스 통합 테스트.
 *
 * <p>주문 생성(PENDING)과 결제 확정이 분리된 인증 → 승인 흐름에서,
 * confirm 의 검증(소유자/상태/금액 위변조)과 성공 확정을 검증한다.
 * FakePaymentGateway 가 항상 승인 성공을 반환하므로 실패 보상 경로는
 * {@code OrderTransactionServiceIntegrationTest}에서 직접 검증한다.
 */
@SpringBootTest
class PaymentConfirmIntegrationTest {

    @Autowired private PaymentApplicationService paymentApplicationService;
    @Autowired private OrderTransactionService orderTransactionService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderModel givenPendingOrder(Long userId) {
        BrandModel brand = brandRepository.save(new BrandModel("나이키", "스포츠"));
        ProductModel product = productRepository.save(
            new ProductModel(brand.getId(), "에어맥스", "러닝화", 50_000L));
        stockRepository.save(StockModel.of(product.getId(), 10));
        return orderTransactionService.createPendingOrder(
            userId, List.of(new OrderItemCommand(product.getId(), 2)), null);
    }

    @DisplayName("정상 confirm 시 주문이 COMPLETED 로 확정된다.")
    @Test
    void completesOrder_whenConfirmSucceeds() {
        // arrange
        OrderModel pending = givenPendingOrder(1L);

        // act — successUrl 에서 받은 paymentKey 로 승인 요청
        OrderInfo result = paymentApplicationService.confirmPayment(
            1L, "fake-payment-key", pending.getId(), pending.getTotalPrice());

        // assert
        assertThat(result.status()).isEqualTo(OrderStatus.COMPLETED.name());
    }

    @DisplayName("요청 금액이 주문 금액과 다르면 BAD_REQUEST — 승인 호출 없이 차단되고 주문은 PENDING 유지 (금액 위변조 방어).")
    @Test
    void rejectsConfirm_whenAmountTampered() {
        // arrange — 주문 금액 100,000원
        OrderModel pending = givenPendingOrder(1L);

        // act — 결제창에서 조작된 금액(100원)으로 승인 시도
        CoreException result = assertThrows(CoreException.class, () ->
            paymentApplicationService.confirmPayment(1L, "fake-payment-key", pending.getId(), 100L));

        // assert — 차단 + 주문 상태 불변
        OrderModel after = orderRepository.findById(pending.getId()).orElseThrow();
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        assertThat(after.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @DisplayName("타 유저의 주문을 confirm 하면 NOT_FOUND — 존재 자체를 노출하지 않는다.")
    @Test
    void rejectsConfirm_whenNotOwner() {
        // arrange — 유저 1의 주문
        OrderModel pending = givenPendingOrder(1L);

        // act — 유저 2가 승인 시도
        CoreException result = assertThrows(CoreException.class, () ->
            paymentApplicationService.confirmPayment(2L, "fake-payment-key", pending.getId(), pending.getTotalPrice()));

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
    }

    @DisplayName("이미 확정된 주문을 다시 confirm 하면 BAD_REQUEST (중복 승인 방지).")
    @Test
    void rejectsConfirm_whenAlreadyCompleted() {
        // arrange — 1차 confirm 으로 COMPLETED 확정
        OrderModel pending = givenPendingOrder(1L);
        paymentApplicationService.confirmPayment(1L, "fake-payment-key", pending.getId(), pending.getTotalPrice());

        // act — 같은 주문 재승인 시도
        CoreException result = assertThrows(CoreException.class, () ->
            paymentApplicationService.confirmPayment(1L, "fake-payment-key", pending.getId(), pending.getTotalPrice()));

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}
