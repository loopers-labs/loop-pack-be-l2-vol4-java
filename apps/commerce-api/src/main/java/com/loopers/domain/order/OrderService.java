package com.loopers.domain.order;

import com.loopers.domain.order.vo.Money;
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

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public Page<OrderModel> getAdminList(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    @Transactional
    public OrderModel placeOrder(OrderModel order, List<OrderLine> lines, Money originalAmount, Money discountAmount) {
        lines.forEach(line -> order.addItem(new OrderItemModel(
                order,
                line.stockId(),
                line.productId(),
                new ProductName(line.productName()),
                line.price(),
                new StockQuantity(line.quantity())
        )));
        order.applyAmounts(originalAmount, discountAmount);
        return orderRepository.save(order);
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
    public OrderModel getByOrderNumberAndUserId(String orderNumber, Long userId) {
        return orderRepository.findByOrderNumberAndUserId(orderNumber, userId)
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
        boolean cancelled = orderRepository.cancelIfRequested(id, userId);
        if (!cancelled) {
            getByUser(id, userId);
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 요청 상태에서만 취소할 수 있습니다.");
        }
        return get(id);
    }

}
