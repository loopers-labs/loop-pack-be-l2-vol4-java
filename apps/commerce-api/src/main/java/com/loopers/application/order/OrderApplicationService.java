package com.loopers.application.order;

import com.loopers.domain.common.PageResult;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderCommand;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderApplicationService {

    private final OrderDomainService orderDomainService;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Transactional
    public OrderInfo.Created place(OrderCriteria.Place command) {
        List<OrderCommand.OrderLine> lines = command.lines().stream()
                .map(OrderCriteria.Line::toDomain)
                .toList();

        Set<Long> productIds = lines.stream()
                .map(OrderCommand.OrderLine::productId)
                .collect(Collectors.toSet());
        List<Product> products = productRepository.findAllByIds(productIds);

        Order order = orderDomainService.create(command.userId(), products, lines);

        productRepository.updateAll(products);
        Order saved = orderRepository.save(order);
        return OrderInfo.Created.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderInfo.Detail getMyOrder(Long userId, Long orderId) {
        Order order = orderRepository.find(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        if (!order.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인 주문만 조회할 수 있습니다.");
        }
        return OrderInfo.Detail.from(order);
    }

    @Transactional(readOnly = true)
    public PageResult<OrderInfo.ListItem> getMyOrders(OrderCriteria.MySearch command) {
        return orderRepository.findAllByUser(command.toDomain()).map(OrderInfo.ListItem::from);
    }

    @Transactional(readOnly = true)
    public OrderInfo.Detail getOrder(Long orderId) {
        Order order = orderRepository.find(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        return OrderInfo.Detail.from(order);
    }

    @Transactional(readOnly = true)
    public PageResult<OrderInfo.ListItem> getAllOrders(OrderCriteria.AdminSearch command) {
        return orderRepository.findAll(command.toDomain()).map(OrderInfo.ListItem::from);
    }
}
