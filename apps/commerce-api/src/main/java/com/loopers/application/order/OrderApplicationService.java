package com.loopers.application.order;

import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class OrderApplicationService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderDomainService orderDomainService;

    @Transactional
    public OrderInfo createOrder(Long userId, OrderCommand command) {
        if (command.items() == null || command.items().isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품은 1개 이상이어야 합니다.");
        }

        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderCommand.Item item : command.items()) {
            if (item.productId() == null) {
                throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 null일 수 없습니다.");
            }
            if (item.quantity() == null || item.quantity() < 1) {
                throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
            }
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }

        Map<Long, ProductModel> products = new HashMap<>();
        for (Long productId : quantities.keySet().stream().sorted().toList()) {
            ProductModel product = productRepository.findWithLock(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
            products.put(productId, product);
        }

        OrderModel order = orderDomainService.place(userId, quantities, products);

        products.values().forEach(productRepository::save);
        OrderModel saved = orderRepository.save(order);
        return OrderInfo.from(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getOrders(Long userId, LocalDate startAt, LocalDate endAt) {
        List<OrderModel> orders;
        if (startAt != null && endAt != null) {
            ZonedDateTime start = startAt.atStartOfDay(ZONE);
            ZonedDateTime end = endAt.plusDays(1).atStartOfDay(ZONE);
            orders = orderRepository.findAllByUserIdAndCreatedAtBetween(userId, start, end);
        } else {
            orders = orderRepository.findAllByUserId(userId);
        }
        return orders.stream().map(OrderInfo::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long userId, Long orderId) {
        OrderModel order = orderRepository.find(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
        if (!order.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN);
        }
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(OrderInfo::from);
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrderForAdmin(Long orderId) {
        OrderModel order = orderRepository.find(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
        return OrderInfo.from(order);
    }
}
