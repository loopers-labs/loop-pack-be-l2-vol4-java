package com.loopers.application.order;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 주문 유스케이스 조립: 상품 조회 → 도메인 서비스 위임 → 영속화.
 */
@RequiredArgsConstructor
@Component
public class OrderFacade {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final OrderService orderService;
    private final ProductService productService;
    private final CouponService couponService;
    private final OrderRepository orderRepository;

    /**
     * 주문 생성. 하나의 쓰기 트랜잭션 안에서:
     *   1) 상품을 비관적 락(PESSIMISTIC_WRITE) 으로 조회 → 2) (couponId 있으면) 쿠폰 사용 + 할인 산정
     *   → 3) 재고 차감 + 주문 생성 → 4) 영속화
     *
     * 동시성 전략:
     * - **재고**: 비관적 락. 인기 상품 동시 주문이 빈번하고 음수 재고 절대 금지라 강한 일관성이 필요.
     * - **쿠폰**: 낙관적 락(@Version, CouponService). 충돌 빈도가 낮아 재시도 비용 최소.
     * - **데드락 회피**: 같은 트랜잭션 내에서 여러 상품을 잠글 때는 productId 오름차순으로 락 획득 순서를 고정.
     *   (주문 A: P1→P2, 주문 B: P2→P1 같은 cross-lock 시나리오를 차단)
     *
     * 쿠폰 무효/낙관적 락 충돌/재고 부족 어디서 실패하든 트랜잭션 전체가 롤백되어 원자성이 보장된다.
     */
    @Transactional
    public OrderInfo createOrder(Long userId, List<OrderItemCommand> items, Long couponId) {
        if (userId == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "주문하려면 로그인이 필요합니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        // 데드락 회피: 락 획득 순서를 productId 오름차순으로 고정한다.
        List<OrderItemCommand> sortedItems = items.stream()
            .sorted(Comparator.comparing(OrderItemCommand::productId))
            .toList();

        List<OrderLine> lines = sortedItems.stream()
            .map(item -> new OrderLine(productService.getProductForUpdate(item.productId()), item.quantity()))
            .toList();

        // 1) 할인 금액 산정 위해 원금을 먼저 계산 (재고 차감 전 가격 기반; 차감 후 OrderItem 도 동일 결과)
        Money originalAmount = lines.stream()
            .map(line -> line.product().getPrice().multiply(line.quantity()))
            .reduce(Money.zero(), Money::plus);

        // 2) 쿠폰 적용 - useCoupon 은 USED 전이 + 할인 금액 반환. 무효 시 예외 → 전체 롤백.
        Money discount = (couponId == null)
            ? Money.zero()
            : couponService.useCoupon(userId, couponId, originalAmount);

        // 3) 도메인 서비스에 위임: 재고 차감(dirty checking) + OrderItem 스냅샷 + Order 생성
        Order order = orderService.createOrder(userId, lines, couponId, discount);
        return OrderInfo.from(orderRepository.save(order));
    }

    /**
     * 주문 단건 조회. 본인 주문만 볼 수 있고, 남의 주문은 존재 자체를 노출하지 않기 위해 NOT_FOUND 로 응답한다.
     */
    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long userId, Long orderId) {
        Order order = orderRepository.find(orderId)
            .filter(o -> o.getUserId().equals(userId))
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
        return OrderInfo.from(order);
    }

    /**
     * 유저의 주문 목록을 조회한다. startAt·endAt 이 모두 주어지면 그 기간(일 단위, inclusive)으로 필터링한다.
     * 둘 중 하나만 오면 잘못된 요청으로 간주한다.
     */
    @Transactional(readOnly = true)
    public List<OrderInfo> getOrders(Long userId, LocalDate startAt, LocalDate endAt) {
        boolean hasStart = startAt != null;
        boolean hasEnd = endAt != null;
        if (hasStart ^ hasEnd) {
            throw new CoreException(ErrorType.BAD_REQUEST, "startAt 와 endAt 은 함께 지정해야 합니다.");
        }
        List<Order> orders;
        if (hasStart) {
            if (startAt.isAfter(endAt)) {
                throw new CoreException(ErrorType.BAD_REQUEST, "startAt 은 endAt 이후일 수 없습니다.");
            }
            ZonedDateTime start = startAt.atStartOfDay(SEOUL);
            ZonedDateTime end = endAt.atTime(LocalTime.MAX).atZone(SEOUL);
            orders = orderRepository.findByUserIdAndPeriod(userId, start, end);
        } else {
            orders = orderRepository.findByUserId(userId);
        }
        return orders.stream().map(OrderInfo::from).toList();
    }
}
