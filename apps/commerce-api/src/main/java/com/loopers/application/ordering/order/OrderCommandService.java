package com.loopers.application.ordering.order;

import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.StockService;
import com.loopers.domain.ordering.order.Order;
import com.loopers.domain.ordering.order.OrderLine;
import com.loopers.domain.ordering.order.OrderRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class OrderCommandService {

    private final OrderRepository orderRepository;
    private final StockService stockService;

    @Transactional
    public Order createPendingOrder(OrderCommand.Create command) {
        validateCommand(command);

        List<OrderCommand.Item> items = command.items();
        Map<Long, Product> products = stockService.decrease(items.stream()
            .map(item -> new StockService.StockRequest(item.productId(), item.quantity()))
            .toList());

        List<OrderLine> orderLines = new ArrayList<>();
        for (OrderCommand.Item item : items) {
            Product product = products.get(item.productId());
            orderLines.add(new OrderLine(
                product.getId(),
                product.getName(),
                product.getPriceAmount(),
                item.quantity()
            ));
        }

        return orderRepository.save(new Order(command.userId(), orderLines));
    }

    @Transactional
    public Order markPaid(Long orderId) {
        Order order = getOrderForUpdate(orderId);
        if (order.isPaid()) {
            return order;
        }
        if (!order.isPaymentPending()) {
            return order;
        }

        order.markPaid();
        return orderRepository.save(order);
    }

    @Transactional
    public Order markPaymentFailedAndRestoreStock(Long orderId) {
        Order order = getOrderForUpdate(orderId);
        if (!order.isPaymentPending()) {
            return order;
        }

        restoreStock(order);
        order.markPaymentFailed();
        return orderRepository.save(order);
    }

    @Transactional
    public Order markCanceledAndRestoreStock(Long orderId) {
        Order order = getOrderForUpdate(orderId);
        if (!order.isPaymentPending()) {
            return order;
        }

        restoreStock(order);
        order.markCanceled();
        return orderRepository.save(order);
    }

    public Order getOrderForPayment(Long orderId) {
        return getOrderForUpdate(orderId);
    }

    private Order getOrderForUpdate(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }

        return orderRepository.findForUpdate(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
    }

    private void restoreStock(Order order) {
        stockService.restore(order.getLines()
            .stream()
            .map(line -> new StockService.StockRequest(line.getProductId(), line.getQuantity()))
            .toList());
    }

    private void validateCommand(OrderCommand.Create command) {
        if (command == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 요청은 필수입니다.");
        }
        if (command.userId() == null || command.userId().isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
        if (command.items() == null || command.items().isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        Set<Long> productIds = new LinkedHashSet<>();
        for (OrderCommand.Item item : command.items()) {
            validateItem(item);
            if (!productIds.add(item.productId())) {
                throw new CoreException(ErrorType.BAD_REQUEST, "같은 상품을 한 주문에 중복으로 담을 수 없습니다.");
            }
        }
    }

    private void validateItem(OrderCommand.Item item) {
        if (item == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 비어있을 수 없습니다.");
        }
        if (item.productId() == null || item.productId() <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
        if (item.quantity() == null || item.quantity() <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
    }

}
