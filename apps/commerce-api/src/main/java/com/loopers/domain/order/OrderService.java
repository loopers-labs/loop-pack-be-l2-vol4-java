package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public Order saveOrder(Order order) {
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다."));
    }

    @Transactional(readOnly = true)
    public PageResult<Order> getOrders(Long userId, PageQuery query, OrderSearchPeriod period) {
        return orderRepository.findAllByUserId(
            userId,
            query,
            period.startDateTime(),
            period.endExclusiveDateTime()
        );
    }

    @Transactional(readOnly = true)
    public PageResult<Order> getOrders(PageQuery query) {
        return orderRepository.findAll(query);
    }
}
