package com.loopers.application.order;

import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 주문 유스케이스 Application Service (스타일 2 - Percival 정통).
 *
 * <p><strong>주문과 결제의 분리 (Round 4)</strong> — 실제 PG 의 인증 → 승인 2단계 모델을 따라
 * 주문 생성과 결제 확정이 서로 다른 HTTP 요청으로 분리되어 있다:
 *
 * <ol>
 *   <li><strong>POST /orders (여기)</strong> — 재고 차감(비관적 락) + 쿠폰 사용(낙관적 락) +
 *       주문 PENDING 저장 후 즉시 커밋. 프론트는 응답받은 orderId/금액으로 PG 결제창(인증)을 연다.</li>
 *   <li><strong>(프론트-PG 구간)</strong> — 유저가 결제창에서 인증. 서버는 관여하지 않는다.
 *       이 구간 동안 재고 row lock 은 이미 해제되어 있어 다른 주문을 막지 않는다.</li>
 *   <li><strong>POST /payments/confirm</strong> ({@link com.loopers.application.payment.PaymentApplicationService})
 *       — 금액 위변조 검증 후 PG 승인 호출. 성공 시 COMPLETED, 실패 시 보상(재고/쿠폰 복구 + FAILED).</li>
 * </ol>
 *
 * <p><strong>방치된 PENDING 주문</strong>: 유저가 결제창에서 이탈하면 주문이 PENDING 으로 남는다.
 * {@link PendingOrderExpirationScheduler}가 주기적으로 PG 결제 여부를 조회한 뒤 만료 처리한다.
 */
@RequiredArgsConstructor
@Service
public class OrderApplicationService {

    private final OrderTransactionService orderTransactionService;
    private final OrderRepository orderRepository;

    /**
     * 주문 생성 — 재고/쿠폰 점유 + PENDING 저장까지. 결제는 포함하지 않는다.
     *
     * <p>응답의 orderId/totalPrice 가 프론트의 PG 결제창 호출 파라미터가 된다.
     *
     * @param couponId 적용할 발급 쿠폰(UserCoupon) id. 미적용 시 null.
     */
    public OrderInfo createOrder(Long userId, List<OrderItemCommand> items, Long couponId) {
        OrderModel pending = orderTransactionService.createPendingOrder(userId, items, couponId);
        return OrderInfo.from(pending);
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
}
