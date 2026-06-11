package com.loopers.application.order;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.StockService;
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
    void createOrderAndPreoccupyStock_Success() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        Long couponIssueId = 42L;
        int quantity = 2;
        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderCreateRequest.Item(productId, quantity)));

        ProductModel product = new ProductModel(100L, "Air Jordan", new BigDecimal("200000"));
        ReflectionTestUtils.setField(product, "id", productId);
        given(productService.getProductsByIds(List.of(productId))).willReturn(List.of(product));
        
        BigDecimal discount = new BigDecimal("40000");
        given(couponService.calculateDiscount(couponIssueId, new BigDecimal("400000"))).willReturn(discount);

        given(orderService.createPendingOrder(eq(userId), anyList(), eq(couponIssueId), any(), any(), any())).willReturn(100L);

        // when
        Long orderId = orderFacade.createOrderAndPreoccupyStock(userId, request, couponIssueId);

        // then
        assertThat(orderId).isEqualTo(100L);
        verify(stockService).decreaseStocksWithLock(anyList());
        verify(orderService).createPendingOrder(eq(userId), anyList(), eq(couponIssueId), any(), any(), any());
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
}
