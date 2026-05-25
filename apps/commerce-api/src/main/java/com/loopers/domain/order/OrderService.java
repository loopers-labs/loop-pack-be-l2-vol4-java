package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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
    public PageResult<Order> getOrders(Long userId, PageQuery query, LocalDate startAt, LocalDate endAt) {
        return orderRepository.findAllByUserId(
            userId,
            query,
            startOfDay(startAt),
            nextDayStart(endAt)
        );
    }

    @Transactional(readOnly = true)
    public PageResult<Order> getOrders(PageQuery query) {
        return orderRepository.findAll(query);
    }

    private ZonedDateTime startOfDay(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.atStartOfDay(ZoneId.systemDefault());
    }

    private ZonedDateTime nextDayStart(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.plusDays(1).atStartOfDay(ZoneId.systemDefault());
    }
}
