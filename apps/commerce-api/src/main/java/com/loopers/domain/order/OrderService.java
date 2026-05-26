package com.loopers.domain.order;

import com.loopers.domain.payment.PaymentResult;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StockService stockService;
    private final PaymentService paymentService;

    /**
     * 주문 생성 + 결제 흐름 (요구사항 F-10, F-11, P-5).
     *
     * 단일 트랜잭션 내에서 다음을 처리한다.
     * 1. 각 상품 검증 + 재고 차감 (실패 시 예외 throw → 트랜잭션 롤백)
     * 2. 주문 PENDING 저장
     * 3. 결제 요청 (외부 PG 동기 호출)
     * 4. 성공: 주문 COMPLETED 로 전이
     *    실패: 주문 CANCELLED + 재고 복구 (예외 X, 보상 처리. 결제 실패 기록 보존을 위해 커밋한다)
     */
    @Transactional
    public OrderModel createOrder(Long userId, List<OrderItemCommand> items) {
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        OrderModel order = new OrderModel(userId);

        for (OrderItemCommand itemCommand : items) {
            ProductModel product = productRepository.findById(itemCommand.productId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + itemCommand.productId() + "] 상품을 찾을 수 없습니다."));

            stockService.deduct(product.getId(), itemCommand.quantity());

            OrderItemModel orderItem = new OrderItemModel(
                order,
                product.getId(),
                product.getName(),
                product.getPrice(),
                itemCommand.quantity()
            );
            order.addItem(orderItem);
        }

        order.confirmTotalPrice();
        OrderModel saved = orderRepository.save(order);

        PaymentResult result = paymentService.process(saved.getId(), saved.getTotalPrice());

        if (result.isSuccess()) {
            saved.complete();
        } else {
            saved.cancel();
            for (OrderItemCommand itemCommand : items) {
                stockService.restore(itemCommand.productId(), itemCommand.quantity());
            }
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<OrderModel> getOrders(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderRepository.findByUserIdAndOrderedAtBetween(userId, startAt, endAt);
    }

    @Transactional(readOnly = true)
    public OrderModel getOrder(Long userId, Long orderId) {
        OrderModel order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));

        if (!order.getUserId().equals(userId)) {
            // 본인 외 주문 접근은 존재 자체를 노출하지 않기 위해 404로 응답 (P-11)
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다.");
        }
        return order;
    }

    @Transactional(readOnly = true)
    public OrderModel findById(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<OrderModel> getAllOrders(int page, int size) {
        return orderRepository.findAll(page, size);
    }
}
