package com.loopers.order.application;

import com.loopers.order.domain.OrderService;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class OrderAdminFacade {

    private final OrderService orderService;

    @Transactional(readOnly = true)
    public PageResult<OrderInfo> getOrders(int page, int size) {
        return orderService.getOrders(new PageQuery(page, size))
            .map(OrderInfo::from);
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long orderId) {
        return OrderInfo.from(orderService.getOrder(orderId));
    }
}
