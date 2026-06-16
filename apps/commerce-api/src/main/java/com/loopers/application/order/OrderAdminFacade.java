package com.loopers.application.order;

import com.loopers.domain.order.OrderAdminService;
import com.loopers.domain.order.OrderModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderAdminFacade {

    private final OrderAdminService orderAdminService;

    public List<OrderModel> getAllOrders() {
        return orderAdminService.getAllOrders();
    }

    public OrderModel getOrder(Long orderId) {
        return orderAdminService.getOrder(orderId);
    }
}
