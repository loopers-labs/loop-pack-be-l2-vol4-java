package com.loopers.order.interfaces.api;

import java.util.List;

public record CreateOrderRequest(List<OrderItemRequest> items) {}
