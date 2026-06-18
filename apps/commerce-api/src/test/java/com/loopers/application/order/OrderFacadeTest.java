package com.loopers.application.order;

import com.loopers.application.coupon.CouponRepository;
import com.loopers.application.payment.PaymentRepository;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductRepository;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGateway.PaymentGatewayResult;
import com.loopers.domain.payment.PaymentMethod;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.product.ProductModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @InjectMocks
    private OrderFacade orderFacade;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductFacade productFacade;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Test
    @DisplayName("주문 요청 시 상품 정보 조회, 재고 차감, 주문 생성이 순차적으로 수행된다.")
    void createOrder_Success_ShouldCallServices() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        int quantity = 2;
        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderCreateRequest.Item(productId, quantity)));
        
        ProductModel product = new ProductModel(100L, "Air Jordan", new BigDecimal("200000"));
        ReflectionTestUtils.setField(product, "id", productId);
        
        given(productRepository.findByIds(List.of(productId))).willReturn(List.of(product));
        given(orderRepository.save(any(OrderModel.class))).willAnswer(invocation -> {
            OrderModel order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 100L);
            return order;
        });

        // when
        Long orderId = orderFacade.createOrder(userId, request);

        // then
        assertThat(orderId).isEqualTo(100L);
        verify(productFacade).decreaseStocks(anyList());
        verify(orderRepository).save(any(OrderModel.class));
    }

    @Test
    @DisplayName("단일 트랜잭션 기반 통합 주문/결제(checkout)가 모든 단계 성공 시 정상 종료된다.")
    void checkout_Success() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        Long couponIssueId = 42L;
        int quantity = 2;
        PaymentMethod method = PaymentMethod.CARD;
        OrderCheckoutRequest request = new OrderCheckoutRequest(
                List.of(new OrderCheckoutRequest.Item(productId, quantity)),
                couponIssueId,
                method
        );

        ProductModel product = new ProductModel(100L, "Air Jordan", new BigDecimal("200000"));
        ReflectionTestUtils.setField(product, "id", productId);
        given(productRepository.findByIds(anyList())).willReturn(List.of(product));

        CouponTemplate template = new CouponTemplate("test", CouponType.FIXED, new BigDecimal("40000"), BigDecimal.ZERO, null, LocalDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(template, "id", 1L);
        CouponIssue couponIssue = new CouponIssue(userId, template);
        given(couponRepository.findIssueById(couponIssueId)).willReturn(Optional.of(couponIssue));

        given(orderRepository.save(any(OrderModel.class))).willAnswer(invocation -> {
            OrderModel order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 100L);
            return order;
        });

        String transactionId = "tx_abc123";
        LocalDateTime approvedAt = LocalDateTime.now();
        given(paymentGateway.requestPayment(eq(100L), eq(new BigDecimal("360000")), eq(method)))
                .willReturn(new PaymentGatewayResult(transactionId, approvedAt));

        // when
        Long resultOrderId = orderFacade.checkout(userId, request);

        // then
        assertThat(resultOrderId).isEqualTo(100L);
        verify(productFacade).decreaseStocksWithLock(anyList());
        verify(paymentGateway).requestPayment(eq(100L), eq(new BigDecimal("360000")), eq(method));
        verify(paymentRepository).save(any(PaymentModel.class));
        verify(couponRepository).saveIssue(couponIssue);
    }
}
