package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    public OrderModel create(Long userId, List<OrderItemData> itemDataList, BigDecimal discountAmount) {
        OrderModel saved = orderRepository.save(OrderModel.create(userId, itemDataList, discountAmount));
        eventPublisher.publish(OrderCreatedEvent.from(saved));
        return saved;
    }

    public OrderModel getById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 주문을 찾을 수 없습니다."));
    }

    public OrderModel getByIdAndValidateOwner(Long orderId, Long userId) {
        OrderModel order = getById(orderId);
        order.validateOwner(userId);
        return order;
    }

    public OrderModel getByOrderNumberAndValidateOwner(String orderNumber, Long userId) {
        OrderModel order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "[orderNumber = " + orderNumber + "] 주문을 찾을 수 없습니다."));
        order.validateOwner(userId);
        return order;
    }

    @Transactional
    public void markPaid(Long orderId) {
        getById(orderId).markPaid();
    }

    @Transactional
    public void markPaymentFailed(Long orderId) {
        getById(orderId).markPaymentFailed();
    }

    public OrderModel getByIdWithItems(Long orderId) {
        return orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 주문을 찾을 수 없습니다."));
    }

    public List<OrderModel> getOrdersByUserIdBetween(Long userId, LocalDate startAt, LocalDate endAt) {
        if (startAt.isAfter(endAt)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "시작일은 종료일보다 이후일 수 없습니다.");
        }
        ZoneId zone = ZoneId.of("Asia/Seoul");
        ZonedDateTime from = startAt.atStartOfDay(zone);
        ZonedDateTime to = endAt.plusDays(1).atStartOfDay(zone);
        return orderRepository.findAllByUserIdAndCreatedAtBetween(userId, from, to);
    }

    public Page<OrderModel> findAll(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }
}
