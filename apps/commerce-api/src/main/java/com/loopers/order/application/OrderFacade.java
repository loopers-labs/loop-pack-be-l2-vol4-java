package com.loopers.order.application;

import com.loopers.coupon.domain.CouponIssueModel;
import com.loopers.coupon.domain.CouponIssueRepository;
import com.loopers.coupon.domain.CouponModel;
import com.loopers.coupon.domain.CouponRepository;
import com.loopers.order.domain.OrderItemModel;
import com.loopers.order.domain.OrderModel;
import com.loopers.order.domain.OrderRepository;
import com.loopers.order.domain.OrderService;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.domain.ProductRepository;
import com.loopers.stock.domain.StockModel;
import com.loopers.stock.domain.StockRepository;
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

        Map<Long, StockModel> stockMap = stockRepository.findAllByProductIds(productIds).stream()
            .collect(Collectors.toMap(StockModel::getProductId, Function.identity()));
        if (!stockMap.keySet().containsAll(quantities.keySet())) {
            throw new CoreException(ErrorType.NOT_FOUND, "재고 정보가 없는 상품이 포함되어 있습니다.");
        }
        quantities.forEach((productId, qty) -> {
            if (stockMap.get(productId).availableStock() < qty) {
                throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
            }
        });

        CouponModel coupon = null;
        Long couponIssueId = null;

        if (couponId != null) {
            coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰이 존재하지 않습니다."));
            if (coupon.isExpired()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
            }
            CouponIssueModel couponIssue = couponIssueRepository.findByUserIdAndCouponIdWithLock(userId, couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "보유하지 않은 쿠폰입니다."));
            couponIssue.use();
            couponIssueRepository.save(couponIssue);
            couponIssueId = couponIssue.getId();
        }

        OrderModel order = orderService.createOrder(userId, products, quantities, coupon, couponIssueId);
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

        List<StockModel> stocks = stockRepository.findAllByProductIdsWithLock(productIds);
        Map<Long, StockModel> stockMap = stocks.stream()
            .collect(Collectors.toMap(StockModel::getProductId, Function.identity()));

        // [fix] 재고 레코드 없는 상품이 포함되어도 예외 없이 통과하던 버그 수정
        if (!stockMap.keySet().containsAll(quantities.keySet())) {
            throw new CoreException(ErrorType.NOT_FOUND, "재고 정보가 없는 상품이 포함되어 있습니다.");
        }

        order.startPayment();
        orderRepository.save(order);

        stocks.forEach(stock -> stock.reserve(quantities.get(stock.getProductId())));
        stocks.forEach(stockRepository::save);

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

        List<StockModel> stocks = stockRepository.findAllByProductIdsWithLock(productIds);
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
