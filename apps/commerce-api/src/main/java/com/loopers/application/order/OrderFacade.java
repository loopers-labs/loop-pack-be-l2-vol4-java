package com.loopers.application.order;

import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentResult;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.error.PaymentFailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 주문 유스케이스 Facade (스타일 2 - Percival 정통).
 *
 * <p>Application Layer 가 조회/저장/결제 호출/예외 throw 를 모두 책임지고,
 * 도메인 협력은 {@link OrderService}(순수 Domain Service)에 위임한다.
 *
 * <p><strong>{@code @Transactional(noRollbackFor = PaymentFailedException.class)} 패턴</strong>:
 * 트랜잭션 안에서 결제 실패 시 CANCELLED 보상 처리(주문 취소 + 재고 복구)는 영속 엔티티
 * 변경으로 dirty checking 에 의해 자동 저장된 뒤, {@link PaymentFailedException} 을 throw 해도
 * 트랜잭션은 정상 커밋된다. 일반 예외(검증 실패 등)는 그대로 롤백된다.
 */
@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final PaymentService paymentService;

    /**
     * 주문 생성 + 결제 유스케이스 (요구사항 F-10, F-11, P-5).
     *
     * <p>흐름:
     * <ol>
     *   <li>한 줄 단위 문맥({@link OrderLine}) 빌드 — 인덱스 매칭 위험 제거</li>
     *   <li>도메인 협력 위임 ({@link OrderService}) — 재고 차감 + 주문 항목 생성</li>
     *   <li>주문 저장 (cascade 로 OrderItem 도 함께 영속화). Stock 변경분은 dirty checking</li>
     *   <li>결제 (외부 PG 동기 호출)</li>
     *   <li>성공: complete() / 실패: cancel() + restoreStocks() — 모두 dirty checking</li>
     *   <li>CANCELLED 면 {@link PaymentFailedException} throw — noRollbackFor 로 커밋 보존</li>
     * </ol>
     */
    @Transactional(noRollbackFor = PaymentFailedException.class)
    public OrderInfo createOrder(Long userId, List<OrderItemCommand> items) {
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        List<OrderLine> orderLines = items.stream()
            .map(cmd -> new OrderLine(
                findProductOrThrow(cmd.productId()),
                findStockOrThrow(cmd.productId()),
                cmd.quantity()
            ))
            .toList();

        OrderModel order = orderService.createWithStockDeduction(userId, orderLines);
        OrderModel saved = orderRepository.save(order);   // 신규 OrderModel 만 명시 저장 (Stock 은 dirty checking)

        PaymentResult result = paymentService.process(saved.getId(), saved.getTotalPrice());

        if (result.isSuccess()) {
            saved.complete();   // dirty checking 으로 커밋 시 반영
            return OrderInfo.from(saved);
        }

        // 결제 실패 보상 처리 — 모두 dirty checking
        saved.cancel();
        orderService.restoreStocks(orderLines);

        // noRollbackFor 덕분에 위 보상 변경분은 커밋되고, 클라이언트엔 400 BAD_REQUEST 응답된다.
        throw new PaymentFailedException("PAYMENT_FAILED: " + result.failureReasonOrDefault());
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getOrders(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderRepository.findByUserIdAndOrderedAtBetween(userId, startAt, endAt).stream()
            .map(OrderInfo::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long userId, Long orderId) {
        OrderModel order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
        if (!order.getUserId().equals(userId)) {
            // 본인 외 주문 접근은 존재 자체를 노출하지 않기 위해 404로 응답 (P-11)
            throw new CoreException(ErrorType.NOT_FOUND,
                "[id = " + orderId + "] 주문을 찾을 수 없습니다.");
        }
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getAllOrders(int page, int size) {
        return orderRepository.findAll(page, size).stream()
            .map(OrderInfo::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrderAdmin(Long orderId) {
        OrderModel order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
        return OrderInfo.from(order);
    }

    private ProductModel findProductOrThrow(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + productId + "] 상품을 찾을 수 없습니다."));
    }

    private StockModel findStockOrThrow(Long productId) {
        return stockRepository.findByProductId(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[productId = " + productId + "] 재고 정보를 찾을 수 없습니다."));
    }
}
