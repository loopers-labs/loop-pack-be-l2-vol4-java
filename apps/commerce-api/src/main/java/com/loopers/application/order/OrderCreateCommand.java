package com.loopers.application.order;

import java.util.List;

/**
 * 주문 생성 커맨드.
 */
public record OrderCreateCommand(Long userId, List<OrderItemCommand> items) {}
