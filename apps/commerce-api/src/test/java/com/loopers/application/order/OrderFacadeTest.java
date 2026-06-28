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
    private com.loopers.application.payment.PaymentFacade paymentFacade;

    @Mock
    private IdempotencyManager idempotencyManager;

    @Mock
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(transactionTemplate.execute(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new org.springframework.transaction.support.SimpleTransactionStatus());
        });
    }

    @Test
    @DisplayName("주문 요청 시 상품 정보 조회, 재고 차감, 주문 생성이 순차적으로 수행된다. (쿠폰 없음)")
    void createOrder_Success_WithoutCoupon() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        int quantity = 2;
        String idempotencyKey = "key-success-1";
        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderCreateRequest.Item(productId, quantity)), null);
        
        ProductModel product = new ProductModel(100L, "Air Jordan", new BigDecimal("200000"));
        ReflectionTestUtils.setField(product, "id", productId);
        
        given(idempotencyManager.getSuccess(idempotencyKey)).willReturn(null);
        given(idempotencyManager.lock(idempotencyKey)).willReturn(true);
        given(productRepository.findByIds(List.of(productId))).willReturn(List.of(product));
        given(orderRepository.save(any(OrderModel.class))).willAnswer(invocation -> {
            OrderModel order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 100L);
            return order;
        });

        // when
        Long orderId = orderFacade.createOrder(userId, request, idempotencyKey);

        // then
        assertThat(orderId).isEqualTo(100L);
        verify(productFacade).decreaseStocks(anyList());
        verify(orderRepository).save(any(OrderModel.class));
        verify(idempotencyManager).saveSuccess(idempotencyKey, 100L);
        verify(idempotencyManager).unlock(idempotencyKey);
    }

    @Test
    @DisplayName("주문 요청 시 쿠폰이 있는 경우 정상적으로 할인이 계산되어 주문이 생성된다.")
    void createOrder_Success_WithCoupon() {
        // given
        Long userId = 1L;
        Long productId = 10L;
        Long couponIssueId = 42L;
        int quantity = 2;
        String idempotencyKey = "key-success-2";
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderCreateRequest.Item(productId, quantity)),
                couponIssueId
        );

        ProductModel product = new ProductModel(100L, "Air Jordan", new BigDecimal("200000"));
        ReflectionTestUtils.setField(product, "id", productId);
        given(idempotencyManager.getSuccess(idempotencyKey)).willReturn(null);
        given(idempotencyManager.lock(idempotencyKey)).willReturn(true);
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

        // when
        Long orderId = orderFacade.createOrder(userId, request, idempotencyKey);

        // then
        assertThat(orderId).isEqualTo(100L);
        verify(productFacade).decreaseStocks(anyList());
        verify(couponRepository).findIssueById(couponIssueId);
        verify(orderRepository).save(any(OrderModel.class));
        verify(idempotencyManager).saveSuccess(idempotencyKey, 100L);
        verify(idempotencyManager).unlock(idempotencyKey);
    }

    @Test
    @DisplayName("이미 성공 처리되어 캐싱된 Idempotency-Key로 요청 시 DB 트랜잭션을 타지 않고 캐싱된 orderId를 반환한다.")
    void createOrder_WithDuplicateSuccessKey_ShouldReturnCachedOrderId() {
        // given
        Long userId = 1L;
        String idempotencyKey = "key-cached-1";
        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderCreateRequest.Item(10L, 2)), null);
        given(idempotencyManager.getSuccess(idempotencyKey)).willReturn(999L);

        // when
        Long orderId = orderFacade.createOrder(userId, request, idempotencyKey);

        // then
        assertThat(orderId).isEqualTo(999L);
        org.mockito.Mockito.verifyNoInteractions(productFacade, orderRepository);
    }

    @Test
    @DisplayName("다른 스레드가 이미 락을 획득하여 처리 중인 키로 진입 시 409 CONFLICT 예외가 발생한다.")
    void createOrder_WithConcurrentKey_ShouldThrowConflict() {
        // given
        Long userId = 1L;
        String idempotencyKey = "key-locked-1";
        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderCreateRequest.Item(10L, 2)), null);
        given(idempotencyManager.getSuccess(idempotencyKey)).willReturn(null);
        given(idempotencyManager.lock(idempotencyKey)).willReturn(false);

        // when & then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> orderFacade.createOrder(userId, request, idempotencyKey))
                .isInstanceOf(com.loopers.support.error.CoreException.class)
                .extracting("errorType")
                .isEqualTo(com.loopers.support.error.ErrorType.CONFLICT);
    }
}
