package com.loopers.application.order;

import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 주문 유스케이스 Application Service (스타일 2 - Percival 정통).
 *
 * <p>Application Layer 가 조회/저장/결제 호출/예외 throw 를 모두 책임지고,
 * 도메인 협력은 {@link OrderService}(순수 Domain Service)에 위임한다.
 *
 * <p><strong>트랜잭션 / 동시성 설계 (Round 4)</strong>:
 * <ul>
 *   <li><strong>원자성</strong> — 재고 차감 / 쿠폰 사용 / 주문 저장 / 결제가 하나의 트랜잭션.
 *       하나라도 실패하면 전체 롤백된다(결제 실패 포함). dirty checking 변경분도 롤백되어 재고/쿠폰이 원복된다.</li>
 *   <li><strong>재고 — 비관적 락</strong> — {@code findByProductIdForUpdate}(SELECT FOR UPDATE)로
 *       동시 차감 Lost Update 를 방지한다. 인기 상품 고충돌 구간이라 줄 세우기가 적합하다.</li>
 *   <li><strong>쿠폰 — 낙관적 락</strong> — {@code @Version}. 동일 쿠폰 동시 주문 시 한 건만 성공하고
 *       나머지는 {@link OptimisticLockingFailureException} → "이미 사용된 쿠폰" 으로 실패한다.</li>
 *   <li><strong>데드락 회피</strong> — 여러 상품 재고 락을 productId 오름차순으로 일관되게 획득한다.</li>
 * </ul>
 */
@RequiredArgsConstructor
@Service
public class OrderApplicationService {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final PaymentService paymentService;

    /**
     * 주문 생성 + (선택)쿠폰 적용 + 결제 유스케이스.
     *
     * <p>흐름:
     * <ol>
     *   <li>쿠폰 사전 조회 + 소유자 검증 — lock 구간 전에 완료해 lock 점유 시간 최소화</li>
     *   <li>productId 오름차순으로 재고 비관적 락 획득 + {@link OrderLine} 빌드</li>
     *   <li>도메인 협력 위임({@link OrderService}) — 재고 차감 + 주문 항목 생성 + 원금 확정</li>
     *   <li>쿠폰 사용 처리(있으면) — 할인 계산 + 낙관적 락 flush + 최종 금액 확정</li>
     *   <li>주문 저장 + 결제. 실패 시 예외 → 전체 롤백</li>
     * </ol>
     *
     * @param couponId 적용할 발급 쿠폰(UserCoupon) id. 미적용 시 null.
     */
    @Transactional
    public OrderInfo createOrder(Long userId, List<OrderItemCommand> items, Long couponId) {
        // 0. 동일 상품 중복 체크 — 같은 productId가 두 번 들어오면 재고 이중 차감 버그로 이어짐
        Set<Long> seen = new HashSet<>();
        for (OrderItemCommand item : items) {
            if (!seen.add(item.productId())) {
                throw new CoreException(ErrorType.BAD_REQUEST,
                    "[productId = " + item.productId() + "] 동일 상품은 하나의 주문 항목으로만 요청할 수 있습니다.");
            }
        }

        // 1. 쿠폰 사전 조회 + 소유자 검증 (lock 구간 전 — 조회 I/O를 lock 점유 시간에서 제외)
        UserCouponModel userCoupon = null;
        CouponModel couponTemplate = null;
        if (couponId != null) {
            userCoupon = userCouponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
            if (!userCoupon.isOwnedBy(userId)) {
                // 타 유저 소유 쿠폰은 존재를 노출하지 않기 위해 동일 메시지로 차단
                throw new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.");
            }
            couponTemplate = couponRepository.findById(userCoupon.getCouponId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 정보를 찾을 수 없습니다."));
        }

        // 2. productId 오름차순 정렬로 재고 락을 일관된 순서로 획득 (데드락 회피) — lock 구간 시작
        List<OrderLine> orderLines = items.stream()
            .sorted(Comparator.comparingLong(OrderItemCommand::productId))
            .map(item -> new OrderLine(
                findProductOrThrow(item.productId()),
                findStockForUpdateOrThrow(item.productId()),
                item.quantity()
            ))
            .toList();

        // 3. 재고 차감 + 주문 항목 생성 + 원금 확정
        OrderModel order = orderService.createWithStockDeduction(userId, orderLines);

        // 4. 쿠폰 사용 처리 (조회는 이미 완료, 상태 변경만 수행)
        if (userCoupon != null) {
            useCoupon(order, userCoupon, couponTemplate);
        }

        // 5. 주문 저장 + 결제 (Stock/UserCoupon 변경분은 dirty checking)
        OrderModel saved = orderRepository.save(order);
        PaymentResult result = paymentService.process(saved.getId(), saved.getTotalPrice());

        if (!result.isSuccess()) {
            // 결제 실패 → 예외 → 트랜잭션 전체 롤백 (재고/쿠폰/주문 모두 원복)
            throw new PaymentFailedException("PAYMENT_FAILED: " + result.failureReasonOrDefault());
        }

        saved.complete();
        return OrderInfo.from(saved);
    }

    /**
     * 쿠폰 할인 계산 + 사용 상태 변경.
     *
     * <p>조회/소유자 검증은 호출 전 완료된 상태. 여기서는 상태 변경(use)과 낙관적 락 flush만 수행한다.
     */
    private void useCoupon(OrderModel order, UserCouponModel userCoupon, CouponModel template) {
        ZonedDateTime now = ZonedDateTime.now();
        template.validateApplicable(Money.of(order.getOriginalAmount()), now);   // 만료 + 최소주문금액
        Money discount = template.calculateDiscount(Money.of(order.getOriginalAmount()));
        userCoupon.use(now);   // AVAILABLE → USED (중복 사용 방어)
        try {
            // 즉시 flush 로 동시 사용 충돌(@Version)을 이 경계 안에서 감지
            userCouponRepository.saveAndFlush(userCoupon);
        } catch (OptimisticLockingFailureException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰입니다.", e);
        }
        order.applyDiscount(discount);
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

    private StockModel findStockForUpdateOrThrow(Long productId) {
        return stockRepository.findByProductIdForUpdate(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[productId = " + productId + "] 재고 정보를 찾을 수 없습니다."));
    }
}
