package com.loopers.application.payment;

import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PgTransactionStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제의 트랜잭션 단위(DB) 작업을 담당한다. 외부 PG 호출은 포함하지 않는다(D5: 외부호출은 트랜잭션 밖).
 * 오케스트레이션은 {@link PaymentFacade} 가 맡는다.
 */
@Service
@RequiredArgsConstructor
public class PaymentApplicationService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    /**
     * 결제건을 등록한다. 주문 검증 + 멱등성(진행중/성공 결제 재사용)을 보장한다.
     */
    @Transactional
    public PaymentModel register(Long userId, PaymentCommand command) {
        OrderModel order = orderRepository.find(command.orderId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + command.orderId() + "] 주문을 찾을 수 없습니다."));
        if (!order.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN);
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제할 수 없는 주문 상태입니다: " + order.getStatus());
        }
        return paymentRepository.findByOrderId(command.orderId()).stream()
            .filter(PaymentModel::isReusable)
            .findFirst()
            .orElseGet(() -> paymentRepository.save(
                PaymentModel.create(userId, command.orderId(), command.cardType(), command.cardNo(), order.getTotalPrice())));
    }

    /**
     * PG 가 거래키를 발급(요청 접수 성공)했을 때 결제건에 반영한다. 상태는 PENDING 유지(결과 대기).
     */
    @Transactional
    public PaymentModel attachTransactionKey(Long paymentId, String transactionKey) {
        PaymentModel payment = getOrThrow(paymentId);
        payment.attachTransactionKey(transactionKey);
        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public PaymentModel getPayment(Long paymentId) {
        return getOrThrow(paymentId);
    }

    /**
     * PG 결과(콜백/폴링)를 결제건과 주문에 반영한다. 이미 확정된 결제는 멱등하게 무시한다.
     * - SUCCESS: 결제 성공 처리 + 주문 PAID
     * - FAILED: 결제 실패 처리 + 주문 CANCELLED + 재고 복원 (D3)
     */
    @Transactional
    public PaymentModel confirm(String transactionKey, PgTransactionStatus pgStatus, String reason) {
        PaymentModel payment = paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "(transactionKey: " + transactionKey + ") 결제건이 존재하지 않습니다."));
        if (!payment.isPending()) {
            return payment; // 멱등: 이미 결과가 확정된 결제
        }
        switch (pgStatus) {
            case SUCCESS -> {
                payment.markSuccess();
                applyOrderPaid(payment.getOrderId());
            }
            case FAILED -> {
                payment.markFailed(reason);
                applyOrderCancelledWithStockRestore(payment.getOrderId());
            }
            case PENDING -> {
                return payment; // 아직 미확정
            }
        }
        return paymentRepository.save(payment);
    }

    private void applyOrderPaid(Long orderId) {
        OrderModel order = orderRepository.find(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
        order.pay();
        orderRepository.save(order);
    }

    private void applyOrderCancelledWithStockRestore(Long orderId) {
        OrderModel order = orderRepository.find(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return; // 이미 취소됨 → 재고 중복 복원 방지
        }
        for (OrderLine line : order.getOrderLines()) {
            ProductModel product = productRepository.findWithLock(line.getProductId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + line.getProductId() + "] 상품을 찾을 수 없습니다."));
            product.increaseStock(line.getQuantity());
            productRepository.save(product);
        }
        order.cancel();
        orderRepository.save(order);
    }

    private PaymentModel getOrThrow(Long paymentId) {
        return paymentRepository.find(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + paymentId + "] 결제를 찾을 수 없습니다."));
    }
}
