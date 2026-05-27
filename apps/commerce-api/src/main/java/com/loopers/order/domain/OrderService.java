package com.loopers.order.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class OrderService {

    public OrderModel getOrThrow(Optional<OrderModel> order) {
        return order.orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문이 존재하지 않습니다."));
    }
}
