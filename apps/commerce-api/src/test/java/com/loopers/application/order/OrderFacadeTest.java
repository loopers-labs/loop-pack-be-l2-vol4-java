package com.loopers.application.order;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.StockService;
import com.loopers.support.error.CoreException;
import com.loopers.domain.product.ProductModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @InjectMocks
    private OrderFacade orderFacade;

    @Mock
    private OrderService orderService;

    @Mock
    private ProductService productService;

    @Mock
    private StockService stockService;

    @Mock
    private CouponService couponService;

    @Mock
    private com.loopers.domain.payment.PaymentService paymentService;

    @Mock
    private com.loopers.domain.payment.PaymentGateway paymentGateway;

    @Test
    @DisplayName("주문 요청 시 상품 정보 조회, 재고 차감, 주문 생성이 순차적으로 수행된다.")
    void createOrder_Success_ShouldCallServices() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        int quantity = 2;
        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderCreateRequest.Item(productId, quantity)));
        
        ProductModel product = new ProductModel(100L, "Air Jordan", new BigDecimal("200000"));
        ReflectionTestUtils.setField(product, "id", productId); // ID 설정 필요
        given(productService.getProductsByIds(List.of(productId))).willReturn(List.of(product));
        given(orderService.createOrder(eq(userId), anyList())).willReturn(100L);

        // when
        Long orderId = orderFacade.createOrder(userId, request);

        // then
        assertThat(orderId).isEqualTo(100L);
        verify(stockService).decreaseStocks(anyList());
        verify(orderService).createOrder(eq(userId), anyList());
    }

    @Test
    @DisplayName("주문 생성 및 재고 가선점 요청 시 비관적 락 재고 차감과 쿠폰 적용이 정상 처리된다.")
    void createPendingOrderAndPreoccupyStockTx_Success() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        Long couponIssueId = 42L;
        int quantity = 2;
        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderCreateRequest.Item(productId, quantity)));

        ProductModel product = new ProductModel(100L, "Air Jordan", new BigDecimal("200000"));
        ReflectionTestUtils.setField(product, "id", productId);
        
        java.util.Map<Long, ProductModel> productMap = java.util.Map.of(productId, product);
        BigDecimal totalOriginalAmount = new BigDecimal("400000");
        BigDecimal discount = new BigDecimal("40000");
        BigDecimal totalPaymentAmount = new BigDecimal("360000");

        given(orderService.createPendingOrder(eq(userId), anyList(), eq(couponIssueId), eq(totalOriginalAmount), eq(discount), eq(totalPaymentAmount))).willReturn(100L);

        // when
        Long orderId = orderFacade.createPendingOrderAndPreoccupyStockTx(userId, request, couponIssueId, productMap, totalOriginalAmount, discount, totalPaymentAmount);

        // then
        assertThat(orderId).isEqualTo(100L);
        verify(stockService).decreaseStocksWithLock(anyList());
        verify(orderService).createPendingOrder(eq(userId), anyList(), eq(couponIssueId), eq(totalOriginalAmount), eq(discount), eq(totalPaymentAmount));
    }

    @Test
    @DisplayName("결제 승인 완료 후처리 요청 시 결제 내역 저장, 주문 완료 처리, 쿠폰 사용 완료 처리가 순차적으로 수행된다.")
    void approvePayment_Success() {
        // given
        Long orderId = 100L;
        Long couponIssueId = 42L;
        java.math.BigDecimal totalOriginalAmount = new java.math.BigDecimal("400000");
        java.math.BigDecimal totalPaymentAmount = new java.math.BigDecimal("360000");
        com.loopers.domain.payment.PaymentMethod method = com.loopers.domain.payment.PaymentMethod.CARD;
        String transactionId = "tx_abc123";
        java.time.LocalDateTime approvedAt = java.time.LocalDateTime.now();

        com.loopers.domain.order.OrderModel order = new com.loopers.domain.order.OrderModel(1L, couponIssueId, totalOriginalAmount, new java.math.BigDecimal("40000"), totalPaymentAmount);
        given(orderService.getOrder(orderId)).willReturn(order);

        // when
        orderFacade.approvePayment(orderId, method, transactionId, approvedAt);

        // then
        verify(orderService).getOrder(orderId);
        verify(paymentService).savePayment(eq(orderId), eq(method), eq(totalPaymentAmount), eq(transactionId), eq(approvedAt));
        verify(orderService).completeOrder(orderId);
        verify(couponService).completeCouponUse(couponIssueId, totalOriginalAmount);
    }

    @Test
    @DisplayName("통합 주문/결제(checkout)가 모든 단계 성공 시 정상 종료된다.")
    void checkout_Success() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        Long couponIssueId = 42L;
        int quantity = 2;
        com.loopers.domain.payment.PaymentMethod method = com.loopers.domain.payment.PaymentMethod.CARD;
        OrderCheckoutRequest request = new OrderCheckoutRequest(
                List.of(new OrderCheckoutRequest.Item(productId, quantity)),
                couponIssueId,
                method
        );

        Long orderId = 100L;
        // Mocking createPendingOrderAndPreoccupyStockTx (Facade 내부 다른 메서드 호출을 모킹하려 하면 스파이가 필요하므로, Facade가 호출하는 서비스를 직접 모킹)
        ProductModel product = new ProductModel(100L, "Air Jordan", new java.math.BigDecimal("200000"));
        org.springframework.test.util.ReflectionTestUtils.setField(product, "id", productId);
        given(productService.getProductsByIds(anyList())).willReturn(List.of(product));
        given(couponService.calculateDiscount(eq(couponIssueId), any())).willReturn(new java.math.BigDecimal("40000"));
        given(orderService.createPendingOrder(eq(userId), anyList(), eq(couponIssueId), any(), any(), any())).willReturn(orderId);

        com.loopers.domain.order.OrderModel order = new com.loopers.domain.order.OrderModel(userId, couponIssueId, new java.math.BigDecimal("400000"), new java.math.BigDecimal("40000"), new java.math.BigDecimal("360000"));
        given(orderService.getOrder(orderId)).willReturn(order);

        String transactionId = "tx_abc123";
        java.time.LocalDateTime approvedAt = java.time.LocalDateTime.now();
        given(paymentGateway.requestPayment(eq(orderId), eq(new java.math.BigDecimal("360000")), eq(method)))
                .willReturn(new com.loopers.domain.payment.PaymentGateway.PaymentGatewayResult(transactionId, approvedAt));

        // when
        Long resultOrderId = orderFacade.checkout(userId, request);

        // then
        assertThat(resultOrderId).isEqualTo(orderId);
        verify(stockService).decreaseStocksWithLock(anyList());
        verify(paymentGateway).requestPayment(eq(orderId), eq(new java.math.BigDecimal("360000")), eq(method));
        verify(paymentService).savePayment(eq(orderId), eq(method), eq(new java.math.BigDecimal("360000")), eq(transactionId), eq(approvedAt));
        verify(orderService).completeOrder(orderId);
        verify(couponService).completeCouponUse(couponIssueId, new java.math.BigDecimal("400000"));
    }

    @Test
    @DisplayName("PG사 결제 요청 실패 시 주문 취소 및 재고가 원복(보상 트랜잭션)된다.")
    void checkout_PaymentGatewayError_ShouldRollback() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        Long couponIssueId = 42L;
        int quantity = 2;
        com.loopers.domain.payment.PaymentMethod method = com.loopers.domain.payment.PaymentMethod.CARD;
        OrderCheckoutRequest request = new OrderCheckoutRequest(
                List.of(new OrderCheckoutRequest.Item(productId, quantity)),
                couponIssueId,
                method
        );

        Long orderId = 100L;
        ProductModel product = new ProductModel(100L, "Air Jordan", new java.math.BigDecimal("200000"));
        org.springframework.test.util.ReflectionTestUtils.setField(product, "id", productId);
        given(productService.getProductsByIds(anyList())).willReturn(List.of(product));
        given(couponService.calculateDiscount(eq(couponIssueId), any())).willReturn(new java.math.BigDecimal("40000"));
        given(orderService.createPendingOrder(eq(userId), anyList(), eq(couponIssueId), any(), any(), any())).willReturn(orderId);

        com.loopers.domain.order.OrderModel order = new com.loopers.domain.order.OrderModel(userId, couponIssueId, new java.math.BigDecimal("400000"), new java.math.BigDecimal("40000"), new java.math.BigDecimal("360000"));
        // 보상 트랜잭션 도중 getOrder를 재호출하므로 두 번 반환하도록 세팅
        given(orderService.getOrder(orderId)).willReturn(order);

        given(paymentGateway.requestPayment(eq(orderId), eq(new java.math.BigDecimal("360000")), eq(method)))
                .willThrow(new RuntimeException("PG Connection Timeout"));

        // when & then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> orderFacade.checkout(userId, request))
                .isInstanceOf(CoreException.class);

        verify(orderService).cancelOrder(orderId);
        verify(stockService).increaseStocks(anyList());
        verify(paymentGateway, never()).cancelPayment(anyString(), any());
    }

    @Test
    @DisplayName("결제 완료 승인 후처리 실패 시 주문 취소, 재고 원복 및 PG 결제 승인 취소(보상 트랜잭션)가 호출된다.")
    void checkout_ApprovePaymentError_ShouldRollbackAndCancelPG() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        Long couponIssueId = 42L;
        int quantity = 2;
        com.loopers.domain.payment.PaymentMethod method = com.loopers.domain.payment.PaymentMethod.CARD;
        OrderCheckoutRequest request = new OrderCheckoutRequest(
                List.of(new OrderCheckoutRequest.Item(productId, quantity)),
                couponIssueId,
                method
        );

        Long orderId = 100L;
        ProductModel product = new ProductModel(100L, "Air Jordan", new java.math.BigDecimal("200000"));
        org.springframework.test.util.ReflectionTestUtils.setField(product, "id", productId);
        given(productService.getProductsByIds(anyList())).willReturn(List.of(product));
        given(couponService.calculateDiscount(eq(couponIssueId), any())).willReturn(new java.math.BigDecimal("40000"));
        given(orderService.createPendingOrder(eq(userId), anyList(), eq(couponIssueId), any(), any(), any())).willReturn(orderId);

        com.loopers.domain.order.OrderModel order = new com.loopers.domain.order.OrderModel(userId, couponIssueId, new java.math.BigDecimal("400000"), new java.math.BigDecimal("40000"), new java.math.BigDecimal("360000"));
        given(orderService.getOrder(orderId)).willReturn(order);

        String transactionId = "tx_abc123";
        java.time.LocalDateTime approvedAt = java.time.LocalDateTime.now();
        given(paymentGateway.requestPayment(eq(orderId), eq(new java.math.BigDecimal("360000")), eq(method)))
                .willReturn(new com.loopers.domain.payment.PaymentGateway.PaymentGatewayResult(transactionId, approvedAt));

        // approvePayment 과정 중 CouponService에서 낙관적 락 충돌 예외가 발생한다고 가정
        doThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(CouponIssue.class, couponIssueId))
                .when(couponService).completeCouponUse(eq(couponIssueId), any());

        // when & then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> orderFacade.checkout(userId, request))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);

        verify(orderService).cancelOrder(orderId);
        verify(stockService).increaseStocks(anyList());
        verify(paymentGateway).cancelPayment(eq(transactionId), eq(new java.math.BigDecimal("360000")));
    }
}
