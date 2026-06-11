package com.loopers.application.order;

import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
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
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
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
 * {@link OrderApplicationService}가 오케스트레이터로서 이 빈을 호출한다.
 *
 * <p><strong>별도 빈으로 분리한 이유</strong>: 같은 클래스 내부 호출(self-invocation)은
 * 스프링 프록시를 거치지 않아 {@code @Transactional} 이 적용되지 않는다.
 * 트랜잭션 경계가 조용히 무효화되는 것을 막기 위해 반드시 다른 빈에서 호출해야 한다.
 *
 * <p><strong>동시성 설계</strong>:
 * <ul>
 *   <li><strong>재고 — 비관적 락</strong> — {@code findByProductIdForUpdate}(SELECT FOR UPDATE).
 *       인기 상품 고충돌 구간이라 줄 세우기가 적합하다. 차감/복구 모두 동일한 락 규율을 따른다.</li>
 *   <li><strong>쿠폰 — 낙관적 락</strong> — {@code @Version}. 동일 쿠폰 동시 주문 시 한 건만 성공.
 *       {@code saveAndFlush} 로 충돌을 트랜잭션 경계 안에서 즉시 감지한다.</li>
 *   <li><strong>데드락 회피</strong> — 재고 락은 항상 productId 오름차순으로 획득한다 (차감/복구 동일).</li>
 * </ul>
 */
@RequiredArgsConstructor
@Service
public class OrderTransactionService {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    /**
     * TX1 — 주문 생성: 재고 차감 + (선택)쿠폰 사용 + 주문 PENDING 저장.
     *
     * <p>이 트랜잭션이 커밋되면 재고 row lock 이 해제된다. 이후의 결제 호출(트랜잭션 밖)이
     * 아무리 지연되어도 다른 주문의 재고 처리를 막지 않는다.
     *
     * <p>흐름:
     * <ol>
     *   <li>동일 상품 중복 검증</li>
     *   <li>쿠폰 사전 조회 + 소유자 검증 — 재고 비관락 획득 전에 완료해 stock row lock 점유 시간 최소화</li>
     *   <li>productId 오름차순으로 재고 비관적 락 획득 + {@link OrderLine} 빌드</li>
     *   <li>도메인 협력 위임({@link OrderService}) — 재고 차감 + 주문 항목 생성 + 원금 확정</li>
     *   <li>쿠폰 사용 처리(있으면) — 할인 계산 + 낙관적 락 flush + 최종 금액 확정</li>
     *   <li>주문 PENDING 저장 후 커밋</li>
     * </ol>
     *
     * @param couponId 적용할 발급 쿠폰(UserCoupon) id. 미적용 시 null.
     */
    @Transactional
    public OrderModel createPendingOrder(Long userId, List<OrderItemCommand> items, Long couponId) {
        // 1. 동일 상품 중복 체크 — 같은 productId가 두 번 들어오면 재고 이중 차감 버그로 이어짐
        Set<Long> seen = new HashSet<>();
        for (OrderItemCommand item : items) {
            if (!seen.add(item.productId())) {
                throw new CoreException(ErrorType.BAD_REQUEST,
                    "[productId = " + item.productId() + "] 동일 상품은 하나의 주문 항목으로만 요청할 수 있습니다.");
            }
        }

        // 2. 쿠폰 사전 조회 + 소유자 검증 (재고 비관락 획득 전 — 조회 I/O를 lock 점유 시간에서 제외)
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

        // 3. productId 오름차순 정렬로 재고 락을 일관된 순서로 획득 (데드락 회피) — lock 구간 시작
        List<OrderLine> orderLines = items.stream()
            .sorted(Comparator.comparingLong(OrderItemCommand::productId))
            .map(item -> new OrderLine(
                findProductOrThrow(item.productId()),
                findStockForUpdateOrThrow(item.productId()),
                item.quantity()
            ))
            .toList();

        // 4. 재고 차감 + 주문 항목 생성 + 원금 확정
        OrderModel order = orderService.createWithStockDeduction(userId, orderLines);

        // 5. 쿠폰 사용 처리 (조회는 이미 완료, 상태 변경만 수행)
        if (userCoupon != null) {
            useCoupon(order, userCoupon, couponTemplate);
            order.attachUserCoupon(userCoupon.getId());   // 결제 실패 보상 시 복구 대상 식별용
        }

        // 6. 주문 PENDING 저장 (Stock/UserCoupon 변경분은 dirty checking) → 커밋과 함께 lock 해제
        return orderRepository.save(order);
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
     * TX2(성공) — 결제 성공 확정: 주문 PENDING → COMPLETED.
     */
    @Transactional
    public OrderInfo completePayment(Long orderId) {
        OrderModel order = findOrderOrThrow(orderId);
        order.complete();
        return OrderInfo.from(order);
    }

    /**
     * TX2(실패) — 결제 실패 보상: 재고 복구 + 쿠폰 복구 + 주문 FAILED.
     *
     * <p>재고 복구도 차감과 동일한 락 규율(productId 오름차순 + SELECT FOR UPDATE)을 따른다.
     * 복구 경로가 동시 주문의 차감 경로와 경합하므로, 규율이 다르면 새로운 데드락 지점이 된다.
     *
     * <p>복구할 쿠폰은 주문에 영속화된 {@link OrderModel#getUserCouponId()}로 식별한다 —
     * 주문 생성과 결제 확정이 서로 다른 HTTP 요청이라 메모리로 전달할 수 없기 때문.
     */
    @Transactional
    public void failPaymentAndRelease(Long orderId) {
        OrderModel order = findOrderOrThrow(orderId);

        // 재고 복구 — 차감과 동일하게 productId 오름차순으로 비관적 락 획득
        order.getItems().stream()
            .sorted(Comparator.comparingLong(OrderItemModel::getProductId))
            .forEach(item -> findStockForUpdateOrThrow(item.getProductId()).restore(item.getQuantity()));

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
     * 쿠폰 할인 계산 + 사용 상태 변경.
     *
     * <p>만료 판정은 발급분 스냅샷({@link UserCouponModel#isExpired})이 기준이다.
     * 조회 경로({@code displayStatus})와 동일한 기준을 보게 하여, 어드민이 템플릿 만료일을
     * 변경해도 "목록에선 만료인데 사용은 되는" 모순이 생기지 않게 한다.
     * 템플릿 검증({@link CouponModel#validateApplicable})은 최소주문금액 조건을 담당한다.
     */
    private void useCoupon(OrderModel order, UserCouponModel userCoupon, CouponModel template) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        if (userCoupon.isExpired(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        template.validateApplicable(Money.of(order.getOriginalAmount()), now);   // 최소주문금액 (+ 템플릿 만료 이중 확인)
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

    private OrderModel findOrderOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
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
