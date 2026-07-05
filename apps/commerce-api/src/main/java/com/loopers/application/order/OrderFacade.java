package com.loopers.application.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.activity.UserActivityEvent;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.DiscountResult;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderLines;
import com.loopers.domain.order.OrderPeriod;
import com.loopers.domain.order.OrderResult;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.event.OrderPlacedEvent;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final UserService userService;
    private final ProductService productService;
    private final BrandService brandService;
    private final OrderService orderService;
    private final StockService stockService;
    private final CouponService couponService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrderInfo placeOrder(Long userId, OrderCommand.Place command) {
        userService.getUser(userId);

        List<OrderLine> lines = command.items().stream()
            .map(item -> {
                ProductModel product = productService.getActive(item.productId());
                BrandModel brand = brandService.getActive(product.getBrandId());
                return OrderLine.snapshotOf(product, brand, item.quantity());
            })
            .toList();

        long totalAmount = OrderLines.of(lines).totalAmount();
        DiscountResult discount = couponService.apply(userId, command.couponId(), totalAmount);

        OrderResult result = orderService.create(userId, lines, discount.amount(), discount.usedCouponId());

        Map<Long, Integer> quantitiesByProductId = result.items().stream()
            .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity));
        stockService.decreaseAll(quantitiesByProductId);

        OrderInfo orderInfo = OrderInfo.from(result.order(), result.items());
        // 시스템 간 전파(판매량 집계): 주문 변경과 같은 TX 로 outbox 에 적재 → Relay 가 order-events 로 발행.
        outboxEventRepository.append(toOutboxEvent(orderInfo.id(), result.items()));
        // in-JVM 부가 로직(알림/행동로깅)은 그대로 ApplicationEvent 로 분리 유지.
        eventPublisher.publishEvent(OrderPlacedEvent.of(orderInfo.id(), userId, orderInfo.finalAmount()));
        eventPublisher.publishEvent(UserActivityEvent.of(userId, UserActivityEvent.Type.ORDER_PLACED, orderInfo.id()));
        return orderInfo;
    }

    private OutboxEvent toOutboxEvent(Long orderId, List<OrderItem> items) {
        try {
            String eventId = UUID.randomUUID().toString();
            String payload = objectMapper.writeValueAsString(OrderPlacedMessage.of(orderId, items));
            // aggregateType=Order → order-events 토픽, aggregateId=orderId → 주문당 이벤트 1건.
            return OutboxEvent.of("Order", orderId, "ORDER_PLACED", eventId, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("주문 outbox payload 직렬화 실패 (orderId=" + orderId + ")", e);
        }
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getMyOrders(Long userId, LocalDate from, LocalDate to) {
        OrderPeriod period = OrderPeriod.of(from, to);
        return orderService.findMine(userId, period).stream()
            .map(result -> OrderInfo.from(result.order(), result.items()))
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getMyOrder(Long userId, Long orderId) {
        OrderResult result = orderService.findOneOwnedBy(userId, orderId);
        return OrderInfo.from(result.order(), result.items());
    }

    @Transactional(readOnly = true)
    public Page<AdminOrderInfo> getAllOrders(int page, int size) {
        Page<OrderResult> orders = orderService.findAll(page, size);
        List<Long> userIds = orders.getContent().stream()
            .map(result -> result.order().getUserId())
            .distinct()
            .toList();
        Map<Long, UserModel> buyersById = userService.getAllByIdIn(userIds).stream()
            .collect(Collectors.toMap(UserModel::getId, Function.identity()));
        return orders.map(result ->
            AdminOrderInfo.from(result.order(), result.items(), buyersById.get(result.order().getUserId())));
    }

    @Transactional(readOnly = true)
    public AdminOrderInfo getOrder(Long orderId) {
        OrderResult result = orderService.findOne(orderId);
        UserModel buyer = userService.getUser(result.order().getUserId());
        return AdminOrderInfo.from(result.order(), result.items(), buyer);
    }
}
