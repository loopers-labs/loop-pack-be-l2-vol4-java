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
    @DisplayName("二쇰Ц ?붿껌 ???곹뭹 ?뺣낫 議고쉶, ?ш퀬 李④컧, 二쇰Ц ?앹꽦???쒖감?곸쑝濡??섑뻾?쒕떎.")
    void createOrder_Success_ShouldCallServices() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        int quantity = 2;
        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderCreateRequest.Item(productId, quantity)));
        
        ProductModel product = new ProductModel(100L, "Air Jordan", new BigDecimal("200000"));
        ReflectionTestUtils.setField(product, "id", productId); // ID ?ㅼ젙 ?꾩슂
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
    @DisplayName("?⑥씪 ?몃옖??뀡 湲곕컲 ?듯빀 二쇰Ц/寃곗젣(checkout)媛 紐⑤뱺 ?④퀎 ?깃났 ???뺤긽 醫낅즺?쒕떎.")
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
        ProductModel product = new ProductModel(100L, "Air Jordan", new java.math.BigDecimal("200000"));
        org.springframework.test.util.ReflectionTestUtils.setField(product, "id", productId);
        given(productService.getProductsByIds(anyList())).willReturn(List.of(product));
        given(couponService.calculateDiscount(eq(couponIssueId), any())).willReturn(new java.math.BigDecimal("40000"));
        given(orderService.createPendingOrder(eq(userId), anyList(), eq(couponIssueId), any(), any(), any())).willReturn(orderId);

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
}
