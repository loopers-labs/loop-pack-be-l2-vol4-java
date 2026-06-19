package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
    private final OrderRepository orderRepository;

    @Transactional
    public OrderInfo createOrder(Long userId, List<OrderItemCommand> items) {
        if (userId == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "주문하려면 로그인이 필요합니다.");
        }
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        List<OrderLine> lines = items.stream()
            .map(item -> new OrderLine(productService.getProduct(item.productId()), item.quantity()))
            .toList();

        // 같은 트랜잭션에서 조회한 상품의 재고 변경은 dirty checking 으로 반영된다.
        Order order = orderService.createOrder(userId, lines);
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
