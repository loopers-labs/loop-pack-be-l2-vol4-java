package com.loopers.application.order;

import com.loopers.domain.order.OrderAdminService;
import com.loopers.domain.order.OrderModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderAdminFacade {

    private final OrderAdminService orderAdminService;

    @Transactional(readOnly = true)
    public List<OrderModel> getAllOrders() {
        return orderAdminService.getAllOrders();
    }

    @Transactional(readOnly = true)
    public OrderModel getOrder(Long orderId) {
        return orderAdminService.getOrder(orderId);
    }
}
