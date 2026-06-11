package com.loopers.domain.order;

import com.loopers.domain.common.PageCriteria;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderReader {

    private final OrderRepository orderRepository;

    public List<Order> getOrders(String userLoginId, LocalDate startAt, LocalDate endAt, Integer page, Integer size) {
        List<Order> orders = orderRepository.findAllByUserLoginId(userLoginId).stream()
            .filter(order -> isWithin(order, startAt, endAt))
            .toList();

        return PageCriteria.of(page, size).slice(orders);
    }

    public Order getOrder(String userLoginId, Long orderId) {
        return orderRepository.findByIdAndUserLoginId(orderId, userLoginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
    }

    public List<Order> getAllOrders(Integer page, Integer size) {
        PageCriteria pageCriteria = PageCriteria.of(page, size);
        return orderRepository.findAll(pageCriteria.page(), pageCriteria.size());
    }

    public Order getOrder(Long orderId) {
        return orderRepository.find(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
    }

    private boolean isWithin(Order order, LocalDate startAt, LocalDate endAt) {
        if (startAt != null && endAt != null && startAt.isAfter(endAt)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "조회 시작일은 종료일보다 늦을 수 없습니다.");
        }

        ZonedDateTime createdAt = order.getCreatedAt();
        if (startAt != null && createdAt.isBefore(startAt.atStartOfDay(ZoneId.systemDefault()))) {
            return false;
        }
        if (endAt != null && createdAt.isAfter(endAt.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()))) {
            return false;
        }

        return true;
    }
}
