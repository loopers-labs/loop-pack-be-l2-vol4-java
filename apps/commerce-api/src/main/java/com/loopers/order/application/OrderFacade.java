package com.loopers.order.application;

import com.loopers.coupon.domain.CouponIssueModel;
import com.loopers.coupon.domain.CouponIssueRepository;
import com.loopers.coupon.domain.CouponModel;
import com.loopers.coupon.domain.CouponRepository;
import com.loopers.coupon.domain.CouponStatus;
import com.loopers.order.domain.OrderItemModel;
import com.loopers.order.domain.OrderModel;
import com.loopers.order.domain.OrderRepository;
import com.loopers.order.domain.OrderService;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.domain.ProductRepository;
import com.loopers.stock.domain.StockModel;
import com.loopers.stock.domain.StockRepository;
import com.loopers.stock.domain.StockService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final StockService stockService;
    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;

    @Transactional
    public OrderInfo createOrder(Long userId, List<OrderItemCommand> commands) {
        return createOrder(userId, commands, null);
    }

    @Transactional
    public OrderInfo createOrder(Long userId, List<OrderItemCommand> commands, Long couponId) {
        if (commands == null || commands.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 비어있을 수 없습니다.");
        }
        List<Long> productIds = commands.stream()
            .map(OrderItemCommand::productId)
            .toList();

        Map<Long, Integer> quantities = commands.stream()
            .collect(Collectors.toMap(OrderItemCommand::productId, OrderItemCommand::quantity));

        List<ProductModel> products = productRepository.findAllByIds(productIds);
        if (products.size() != productIds.size()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품이 포함되어 있습니다.");
        }

        Long couponIssueId = null;
        long discountAmount = 0L;

        if (couponId != null) {
            CouponModel coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰이 존재하지 않습니다."));
            if (coupon.isExpired()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
            }
            CouponIssueModel couponIssue = couponIssueRepository.findByUserIdAndCouponId(userId, couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "보유하지 않은 쿠폰입니다."));
            if (couponIssue.getStatus() != CouponStatus.AVAILABLE) {
                throw new CoreException(ErrorType.BAD_REQUEST, "사용 불가능한 쿠폰입니다.");
            }

            long originalAmount = products.stream()
                .mapToLong(p -> p.getPrice() * quantities.getOrDefault(p.getId(), 0))
                .sum();
            discountAmount = coupon.calculateDiscount(originalAmount);
            couponIssueId = couponIssue.getId();
        }

        OrderModel order = orderService.createOrder(userId, products, quantities, couponIssueId, discountAmount);
        return OrderInfo.from(orderRepository.save(order));
    }

    @Transactional
    public OrderInfo startPayment(Long userId, Long orderId) {
        OrderModel order = orderService.getOrThrow(orderRepository.find(orderId));
        orderService.checkOwnership(order, userId);

        Map<Long, Integer> quantities = order.getItems().stream()
            .collect(Collectors.toMap(OrderItemModel::getProductId, OrderItemModel::getQuantity));

        List<Long> productIds = order.getItems().stream()
            .map(OrderItemModel::getProductId)
            .toList();

        List<StockModel> stocks = stockRepository.findAllByProductIds(productIds);
        Map<Long, StockModel> stockMap = stocks.stream()
            .collect(Collectors.toMap(StockModel::getProductId, Function.identity()));

        // [fix] 재고 레코드 없는 상품이 포함되어도 예외 없이 통과하던 버그 수정
        if (!stockMap.keySet().containsAll(quantities.keySet())) {
            throw new CoreException(ErrorType.NOT_FOUND, "재고 정보가 없는 상품이 포함되어 있습니다.");
        }

        stocks.forEach(stock -> stock.reserve(quantities.get(stock.getProductId())));
        stocks.forEach(stockRepository::save);

        if (order.getCouponIssueId() != null) {
            CouponIssueModel couponIssue = couponIssueRepository.findByIdWithLock(order.getCouponIssueId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 발급 정보가 존재하지 않습니다."));
            couponIssue.use();
            couponIssueRepository.save(couponIssue);
        }

        return OrderInfo.from(order);
    }

    @Transactional
    public OrderInfo confirmPayment(Long userId, Long orderId) {
        OrderModel order = orderService.getOrThrow(orderRepository.find(orderId));
        orderService.checkOwnership(order, userId);

        Map<Long, Integer> quantities = order.getItems().stream()
            .collect(Collectors.toMap(OrderItemModel::getProductId, OrderItemModel::getQuantity));

        List<Long> productIds = order.getItems().stream()
            .map(OrderItemModel::getProductId)
            .toList();

        List<StockModel> stocks = stockRepository.findAllByProductIds(productIds);
        Map<Long, StockModel> stockMap = stocks.stream()
            .collect(Collectors.toMap(StockModel::getProductId, Function.identity()));

        if (!stockMap.keySet().containsAll(quantities.keySet())) {
            throw new CoreException(ErrorType.NOT_FOUND, "재고 정보가 없는 상품이 포함되어 있습니다.");
        }

        stocks.forEach(stock -> stock.confirm(quantities.get(stock.getProductId())));
        stocks.forEach(stockRepository::save);

        order.confirm();
        return OrderInfo.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getOrders(Long userId, LocalDate startAt, LocalDate endAt) {
        ZonedDateTime start = startAt.atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime end = endAt.plusDays(1).atStartOfDay(ZoneId.systemDefault());
        return orderRepository.findAllByUserId(userId, start, end).stream()
            .map(OrderInfo::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long userId, Long orderId) {
        OrderModel order = orderService.getOrThrow(orderRepository.find(orderId));
        orderService.checkOwnership(order, userId);
        return OrderInfo.from(order);
    }
}
