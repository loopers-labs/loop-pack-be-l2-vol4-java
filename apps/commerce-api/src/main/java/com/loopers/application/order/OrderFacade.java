package com.loopers.application.order;

import com.loopers.domain.order.OrderProductCommand;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderResult;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderFacade {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderService orderService = new OrderService();

    @Transactional
    public OrderInfo createOrder(String userLoginId, List<OrderProductCommand> commands) {
        Map<Long, ProductModel> productsById = findProductsById(commands);
        OrderResult result = orderService.createOrder(userLoginId, commands, productsById);
        productsById.values().forEach(productRepository::save);
        return OrderInfo.from(new OrderResult(orderRepository.save(result.order()), result.failures()));
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getOrders(String userLoginId, LocalDate startAt, LocalDate endAt, Integer page, Integer size) {
        List<OrderModel> orders = orderRepository.findAllByUserLoginId(userLoginId).stream()
            .filter(order -> isWithin(order, startAt, endAt))
            .toList();

        return paginate(orders, page, size).stream()
            .map(OrderInfo::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(String userLoginId, Long orderId) {
        return orderRepository.findByIdAndUserLoginId(orderId, userLoginId)
            .map(OrderInfo::from)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getAllOrders(Integer page, Integer size) {
        return paginate(orderRepository.findAll(), page, size).stream()
            .map(OrderInfo::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long orderId) {
        return orderRepository.find(orderId)
            .map(OrderInfo::from)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
    }

    private Map<Long, ProductModel> findProductsById(List<OrderProductCommand> commands) {
        return commands.stream()
            .map(OrderProductCommand::productId)
            .distinct()
            .map(productRepository::find)
            .flatMap(Optional::stream)
            .collect(Collectors.toMap(ProductModel::getId, Function.identity()));
    }

    private boolean isWithin(OrderModel order, LocalDate startAt, LocalDate endAt) {
        if (startAt != null && endAt != null && startAt.isAfter(endAt)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "조회 시작일은 종료일보다 늦을 수 없습니다.");
        }

        ZonedDateTime createdAt = order.getCreatedAt();
        if (startAt != null && createdAt.isBefore(startAt.atStartOfDay(ZoneId.systemDefault()))) {
            return false;
        }
        if (endAt != null && createdAt.isAfter(endAt.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()))) {
            return false;
        }

        return true;
    }

    private List<OrderModel> paginate(List<OrderModel> orders, Integer page, Integer size) {
        int requestedPage = page == null ? DEFAULT_PAGE : page;
        int requestedSize = size == null ? DEFAULT_SIZE : size;
        validatePage(requestedPage, requestedSize);

        int fromIndex = requestedPage * requestedSize;
        if (fromIndex >= orders.size()) {
            return List.of();
        }

        int toIndex = Math.min(fromIndex + requestedSize, orders.size());
        return orders.subList(fromIndex, toIndex);
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 1 이상이어야 합니다.");
        }
    }
}
