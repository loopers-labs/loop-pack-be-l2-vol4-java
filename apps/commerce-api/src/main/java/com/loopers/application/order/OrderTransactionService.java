package com.loopers.application.order;

import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.application.activity.UserActionEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 주문 유스케이스의 트랜잭션 단위 작업 모음.
 *
 * <p>결제(외부 I/O)를 DB 트랜잭션 밖에 두기 위해 트랜잭션 경계를 메서드 단위로 분리한다.
 * 별도 빈인 이유: 같은 클래스 내부 호출(self-invocation)은 스프링 프록시를 거치지 않아
 * {@code @Transactional} 이 적용되지 않기 때문.
 *
 * <p><strong>무점유 주문 + 승인 직전 점유 설계</strong>:
 * <ul>
 *   <li><strong>주문 생성 = 무점유 견적</strong> — 재고/쿠폰을 확인만 하고 점유하지 않는다.
 *       결제창에서 이탈해도 아무 자원도 잠기지 않는다.</li>
 *   <li><strong>자원 점유는 결제 승인 직전</strong>({@link #bindResources}) — 재고는 조건부 원자
 *       UPDATE, 쿠폰은 낙관적 락으로 확정한다. 점유 실패자는 PG 승인을 호출하지 않으므로
 *       돈이 나가기 전에 탈락한다 — "결제됐는데 품절" 환불이 정상 흐름에서 발생하지 않는다.</li>
 *   <li><strong>재고 점유 시간 ≒ PG 승인 호출 시간</strong> — 예약형(주문 시 차감)의
 *       장시간 잠금도, 순수 후차감(승인 후 차감)의 환불 비용도 피하는 지점.</li>
 *   <li><strong>데드락 회피</strong> — 원자 UPDATE 도 행 X락을 커밋까지 유지하므로,
 *       다중 상품 차감/복구는 항상 productId 오름차순으로 수행한다.</li>
 * </ul>
 */
@RequiredArgsConstructor
@Service
public class OrderTransactionService {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final UserCouponRepository userCouponRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * TX1 — 주문 생성 (무점유 견적): 재고/쿠폰 검증 + 금액 확정 + 주문 PENDING 저장.
     *
     * <p>재고는 {@code hasEnough} 확인만(락 없음 — 품절 상품으로 결제창까지 가는 것을 막는
     * UX 게이트), 쿠폰은 검증 + 할인 계산만 수행한다. 점유(차감/USED 전환)는 일절 없다 —
     * 동시성 보장은 {@link #bindResources}의 원자 UPDATE/낙관적 락이 책임진다.
     *
     * @param couponId 적용할 발급 쿠폰(UserCoupon) id. 미적용 시 null.
     */
    @Transactional
    public OrderModel createPendingOrder(Long userId, List<OrderItemCommand> items, Long couponId) {
        // 1. 동일 상품 중복 체크 — 같은 productId가 두 번 들어오면 이중 차감 견적이 된다
        Set<Long> seen = new HashSet<>();
        for (OrderItemCommand item : items) {
            if (!seen.add(item.productId())) {
                throw new CoreException(ErrorType.BAD_REQUEST,
                    "[productId = " + item.productId() + "] 동일 상품은 하나의 주문 항목으로만 요청할 수 있습니다.");
            }
        }

        // 2. 상품/재고 조회 (일반 조회 — 락 없음) + 재고 확인 + 원금 확정
        List<OrderLine> orderLines = items.stream()
            .map(item -> new OrderLine(
                findProductOrThrow(item.productId()),
                findStockOrThrow(item.productId()),
                item.quantity()
            ))
            .toList();
        OrderModel order = orderService.createOrder(userId, orderLines);

        // 3. 쿠폰 견적 — 검증 + 할인 적용. USED 전환은 하지 않는다 (확정은 bindResources 에서)
        if (couponId != null) {
            quoteCoupon(order, userId, couponId);
        }

        // 4. 주문 PENDING 저장 — 아무 자원도 점유하지 않은 견적 상태
        OrderModel saved = orderRepository.save(order);
        // 유저 행동 로깅 (커밋 후 리스너가 기록)
        eventPublisher.publishEvent(UserActionEvent.order(userId, saved.getId()));
        return saved;
    }

    /**
     * 결제 승인(confirm) 전 검증 — 소유자 / 상태 / 금액.
     *
     * <p><strong>금액 검증이 핵심이다</strong>: confirm 요청의 amount 는 브라우저를 거쳐 온 값이라
     * 신뢰할 수 없다. 결제창에 조작된 금액으로 인증한 뒤 원래 주문을 승인시키는 위변조를
     * DB 에 저장된 주문 금액과의 대조로 차단한다.
     */
    @Transactional(readOnly = true)
    public void validateConfirmable(Long userId, Long orderId, Long amount) {
        OrderModel order = findOrderOrThrow(orderId);
        if (!order.getUserId().equals(userId)) {
            // 타 유저 주문은 존재를 노출하지 않기 위해 동일 메시지로 차단
            throw new CoreException(ErrorType.NOT_FOUND,
                "[id = " + orderId + "] 주문을 찾을 수 없습니다.");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 대기 상태의 주문이 아닙니다.");
        }
        if (!order.getTotalPrice().equals(amount)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액이 주문 금액과 일치하지 않습니다.");
        }
    }

    /**
     * TX2a — 자원 점유 (결제 승인 직전): 주문 PAYMENT_IN_PROGRESS 전이 + 재고 원자 차감 + 쿠폰 사용 확정.
     *
     * <p>주문 행을 비관적 락으로 잡고 시작한다 — 같은 주문의 중복 confirm 이 동시에 들어와도
     * 한 요청만 PENDING → PAYMENT_IN_PROGRESS 전이에 성공하고 나머지는 상태 가드에서 거부된다.
     *
     * <p>재고 차감은 {@code UPDATE ... WHERE quantity >= ?} 조건부 원자 UPDATE — "확인"이 아니라
     * "차감하면서 확인"이므로 확인-차감 사이의 틈(TOCTOU)이 없다. 영향 행 0 = 재고 부족이며,
     * 이 경우 트랜잭션 전체가 롤백되어(이미 차감된 다른 항목 포함) 점유는 없던 일이 된다.
     *
     * <p>여기서 실패하면 호출자는 PG 승인을 호출하지 않는다 — 유저에게 청구되지 않는다.
     */
    @Transactional
    public void bindResources(Long orderId) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        OrderModel order = findOrderForUpdateOrThrow(orderId);
        order.startPayment(now);   // PENDING → PAYMENT_IN_PROGRESS (중복 confirm 가드)

        // 재고 원자 차감 — productId 오름차순 (원자 UPDATE 도 행 락을 커밋까지 유지하므로 정렬 필수)
        List<OrderItemModel> sortedItems = order.getItems().stream()
            .sorted(Comparator.comparingLong(OrderItemModel::getProductId))
            .toList();
        for (OrderItemModel item : sortedItems) {
            int affected = stockRepository.deductAtomically(item.getProductId(), item.getQuantity());
            if (affected == 0) {
                // 상품 존재는 주문 시점에 검증됨 — 0행 = 재고 부족. 롤백으로 전체 점유 취소.
                throw new CoreException(ErrorType.BAD_REQUEST,
                    "[productId = " + item.getProductId() + "] 재고가 부족하여 주문을 진행할 수 없습니다.");
            }
        }

        // 쿠폰 사용 확정 — 견적 때 검증했지만 견적~확정 사이의 변화(만료/타 기기 사용)를 재검증
        if (order.getUserCouponId() != null) {
            UserCouponModel userCoupon = userCouponRepository.findById(order.getUserCouponId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
            if (userCoupon.isExpired(now)) {
                throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
            }
            userCoupon.use(now);   // AVAILABLE → USED (중복 사용 방어)
            try {
                // 즉시 flush 로 동시 사용 충돌(@Version)을 이 경계 안에서 감지
                userCouponRepository.saveAndFlush(userCoupon);
            } catch (OptimisticLockingFailureException e) {
                throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰입니다.", e);
            }
        }
    }

    /**
     * TX2b(성공) — 결제 승인 성공 확정: 주문 PAYMENT_IN_PROGRESS → COMPLETED.
     *
     * <p>비관적 락 조회로 만료 스케줄러의 보상 처리와 직렬화한다.
     */
    @Transactional
    public OrderInfo completePayment(Long orderId) {
        OrderModel order = findOrderForUpdateOrThrow(orderId);
        order.complete();
        return OrderInfo.from(order);
    }

    /**
     * 견적 폐기 — 자원 점유 전에 실패한 주문을 FAILED 로 닫는다 (복구할 자원 없음).
     *
     * <p>PENDING 이 아니면 조용히 넘어간다 — 같은 주문의 다른 confirm 이 이미 점유에
     * 성공(PAYMENT_IN_PROGRESS)했을 수 있고, 그 주문을 건드리면 안 되기 때문.
     */
    @Transactional
    public void markOrderFailed(Long orderId) {
        OrderModel order = findOrderForUpdateOrThrow(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }
        order.fail();
    }

    /**
     * TX2b(실패) — 점유 자원 보상: 재고 원자 복구 + 쿠폰 복구 + 주문 FAILED.
     *
     * <p>PAYMENT_IN_PROGRESS(점유 완료)인 주문만 처리한다. 다른 상태면 조용히 넘어간다 —
     * 스케줄러와 confirm 응답 처리가 경합했을 때 진 쪽이 이중 보상하는 것을 막기 위함.
     * (예: 스케줄러가 PG 조회로 결제 확인 후 COMPLETED 확정 직후, 늦게 도착한 보상 요청)
     */
    @Transactional
    public void releaseAndFail(Long orderId) {
        OrderModel order = findOrderForUpdateOrThrow(orderId);
        if (order.getStatus() != OrderStatus.PAYMENT_IN_PROGRESS) {
            return;
        }

        // 재고 복구 — 차감과 동일하게 productId 오름차순
        order.getItems().stream()
            .sorted(Comparator.comparingLong(OrderItemModel::getProductId))
            .forEach(item -> stockRepository.restoreAtomically(item.getProductId(), item.getQuantity()));

        // 쿠폰 복구 (USED → AVAILABLE)
        if (order.getUserCouponId() != null) {
            UserCouponModel userCoupon = userCouponRepository.findById(order.getUserCouponId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
            userCoupon.cancelUse();
            userCouponRepository.save(userCoupon);
        }

        order.fail();
    }

    /**
     * 쿠폰 견적 — 소유자/만료/최소주문금액 검증 + 할인 적용 + 주문에 연결.
     *
     * <p>검증과 할인 계산 모두 <strong>발급 시점 혜택 스냅샷</strong> 기준이다 — 어드민이 이후
     * 템플릿을 수정해도 발급분의 혜택은 변하지 않으며("발급은 그 시점의 약속"),
     * 주문 경로에서 템플릿 재조회가 필요 없다. USED 전환은 하지 않는다(확정은 bindResources).
     */
    private void quoteCoupon(OrderModel order, Long userId, Long couponId) {
        UserCouponModel userCoupon = userCouponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        if (!userCoupon.isOwnedBy(userId)) {
            // 타 유저 소유 쿠폰은 존재를 노출하지 않기 위해 동일 메시지로 차단
            throw new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.");
        }

        if (userCoupon.getStatus() == CouponStatus.USED) {
            // 이미 사용된 쿠폰은 견적 단계에서 조기 차단 — 결제창까지 갔다가 confirm 에서 실패하는 UX 방지
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰입니다.");
        }
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        userCoupon.validateApplicable(Money.of(order.getOriginalAmount()), now);   // 만료 + 최소주문금액 (스냅샷 기준)
        Money discount = userCoupon.calculateDiscount(Money.of(order.getOriginalAmount()));
        order.applyDiscount(discount);
        order.attachUserCoupon(userCoupon.getId());
    }

    private OrderModel findOrderOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
    }

    private OrderModel findOrderForUpdateOrThrow(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
    }

    private ProductModel findProductOrThrow(Long productId) {
        ProductModel product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        if (product.getDeletedAt() != null) {
            throw new CoreException(ErrorType.NOT_FOUND,
                "[id = " + productId + "] 상품을 찾을 수 없습니다.");
        }
        return product;
    }

    private StockModel findStockOrThrow(Long productId) {
        return stockRepository.findByProductId(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[productId = " + productId + "] 재고 정보를 찾을 수 없습니다."));
    }

}

