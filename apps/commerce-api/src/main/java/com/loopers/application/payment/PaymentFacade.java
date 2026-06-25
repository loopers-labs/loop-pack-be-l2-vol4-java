package com.loopers.application.payment;

import com.loopers.application.coupon.CouponRepository;
import com.loopers.application.order.OrderRepository;
import com.loopers.application.product.ProductFacade;
import com.loopers.domain.payment.PaymentMethod;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGateway.PaymentGatewayResult;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentTempStorage paymentTempStorage;
    private final OrderRepository orderRepository;
    private final ProductFacade productFacade;
    private final CouponRepository couponRepository;
    private final NotificationService notificationService;

    public PaymentStatus getPaymentStatus(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .map(PaymentModel::getStatus)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 내역을 찾을 수 없습니다."));
    }

    public Long processPayment(Long orderId, PaymentMethod method, BigDecimal amount) {
        // 1. READY 상태로 저장 (단일 데이터 변경 작업, save API 자체 트랜잭션으로 바로 커밋)
        PaymentModel payment = new PaymentModel(orderId, method, amount);
        payment = paymentRepository.save(payment);
        Long paymentId = payment.getId();

        // 2. Redis에 TTL 10초 설정 (추상화된 TempStorage 사용)
        try {
            paymentTempStorage.setRetryCount(paymentId, 0, Duration.ofSeconds(10));
        } catch (Exception e) {
            log.error("Failed to write to Redis for payment retry trace: {}", paymentId, e);
        }

        // 3. 외부 PG API 호출 (트랜잭션 바깥이므로 DB 락 및 커넥션 점유 없음)
        try {
            PaymentGatewayResult result = paymentGateway.requestPayment(orderId, amount, method);
            
            // 4. 성공 시 상태 APPROVED로 변경 (단일 데이터 변경 작업)
            PaymentModel targetPayment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 내역을 찾을 수 없습니다."));
            targetPayment.approve(result.transactionId(), result.approvedAt());
            paymentRepository.save(targetPayment);
            
            // 5. 성공 후 Redis 키 제거
            paymentTempStorage.deleteRetryKey(paymentId);
        } catch (Exception e) {
            log.warn("Payment PG request timeout or failed, keeping READY status for payment id: {}", paymentId, e);
        }

        return paymentId;
    }

    @Transactional
    public void retryOrCompensatePayment(Long paymentId) {
        retryOrCompensatePayment(paymentId, false);
    }

    @Transactional
    public void retryOrCompensatePayment(Long paymentId, boolean isFallback) {
        PaymentModel payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 내역을 찾을 수 없습니다."));

        if (payment.getStatus() != PaymentStatus.READY) {
            return;
        }

        PaymentGateway.PaymentGatewayQueryResult queryResult = paymentGateway.queryPaymentStatus(payment.getOrderId());

        if (!isFallback && queryResult.status() == com.loopers.domain.payment.PaymentGatewayStatus.APPROVED) {
            payment.approve(queryResult.transactionId(), queryResult.approvedAt());
            paymentRepository.save(payment);

            com.loopers.domain.order.OrderModel order = orderRepository.findById(payment.getOrderId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문 내역을 찾을 수 없습니다."));
            order.complete();
            orderRepository.save(order);

            paymentTempStorage.deleteRetryKey(paymentId);
        } else {
            Integer count = paymentTempStorage.getRetryCount(paymentId);
            if (count == null) {
                count = 0;
            }

            if (!isFallback && queryResult.status() == com.loopers.domain.payment.PaymentGatewayStatus.PENDING && count < 2) {
                paymentTempStorage.setRetryCount(paymentId, count + 1, Duration.ofSeconds(10));
            } else {
                com.loopers.domain.order.OrderModel order = orderRepository.findById(payment.getOrderId())
                        .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문 내역을 찾을 수 없습니다."));

                // Fallback 보정이면서 PG 결과가 APPROVED(결제성공)라면 즉시 결제 취소 API를 연동한다.
                if (isFallback && queryResult.status() == com.loopers.domain.payment.PaymentGatewayStatus.APPROVED) {
                    try {
                        log.info("Fallback correction: Canceling actual PG payment for payment: {}", paymentId);
                        paymentGateway.cancelPayment(queryResult.transactionId(), payment.getAmount());
                        try {
                            notificationService.sendPaymentRefund(order.getUserId(), paymentId);
                        } catch (Exception ne) {
                            log.error("Failed to send payment refund notification for user: {}, payment: {}", order.getUserId(), paymentId, ne);
                        }
                    } catch (Exception e) {
                        log.error("Failed to cancel PG payment for payment: {}", paymentId, e);
                    }
                }

                payment.fail();
                paymentRepository.save(payment);

                order.cancel();
                orderRepository.save(order);

                // 1. 재고 복구 (롤백)
                if (order.getItems() != null && !order.getItems().isEmpty()) {
                    java.util.List<com.loopers.application.product.ProductFacade.StockRequest> stockRequests = order.getItems().stream()
                            .map(item -> new com.loopers.application.product.ProductFacade.StockRequest(item.getProductId(), item.getQuantity()))
                            .toList();
                    productFacade.increaseStocks(stockRequests);
                }

                // 2. 쿠폰 복구 (롤백)
                if (order.getCouponIssueId() != null) {
                    com.loopers.domain.coupon.CouponIssue couponIssue = couponRepository.findIssueById(order.getCouponIssueId())
                            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 발급 내역을 찾을 수 없습니다."));
                    couponIssue.restore();
                    couponRepository.saveIssue(couponIssue);
                }

                // 3. 알림 서비스 호출 (Fallback 스케줄러 보정이 아닐 때만 발송)
                if (!isFallback) {
                    try {
                        notificationService.sendPaymentTimeout(order.getUserId(), paymentId);
                    } catch (Exception e) {
                        log.error("Failed to send payment timeout notification for user: {}, payment: {}", order.getUserId(), paymentId, e);
                    }
                }

                paymentTempStorage.deleteRetryKey(paymentId);
            }
        }
    }
}
