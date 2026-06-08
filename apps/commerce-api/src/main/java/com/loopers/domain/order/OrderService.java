package com.loopers.domain.order;

import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.domain.product.vo.StockQuantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderTotalPolicy orderTotalPolicy;

    @Transactional(readOnly = true)
    public Page<OrderModel> getAdminList(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    @Transactional
    public OrderModel placeOrder(OrderModel order, List<ProductStockModel> stocks, List<OrderLine> lines) {
        buildItems(order, stocks, lines);
        order.updateTotal(orderTotalPolicy.calculate(order.getItems()));
        return orderRepository.save(order);
    }

    private void buildItems(OrderModel order, List<ProductStockModel> stocks, List<OrderLine> lines) {
        Map<Long, ProductStockModel> stockMap = stocks.stream()
                .collect(Collectors.toMap(ProductStockModel::getId, s -> s));
        lines.forEach(line -> {
            ProductStockModel stock = stockMap.get(line.stockId());
            order.addItem(new OrderItemModel(
                    order,
                    stock.getId(),
                    stock.getProduct().getId(),
                    new ProductName(stock.getProduct().getName()),
                    stock.getPrice(),
                    new StockQuantity(line.quantity())
            ));
        });
    }

    @Transactional(readOnly = true)
    public OrderModel get(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public OrderModel getByUser(Long id, Long userId) {
        return orderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public OrderModel getByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderNumber = " + orderNumber + "] 주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<OrderModel> getList(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        if (startAt == null && endAt == null) {
            return orderRepository.findAllByUserId(userId);
        }
        return orderRepository.findAllByUserIdWithDateRange(userId, startAt, endAt);
    }

    @Transactional
    public OrderModel complete(Long id) {
        OrderModel order = get(id);
        order.complete();
        return order;
    }

    @Transactional
    public OrderModel cancel(Long id, Long userId) {
        OrderModel order = getByUser(id, userId);
        order.cancel();
        return order;
    }

}
