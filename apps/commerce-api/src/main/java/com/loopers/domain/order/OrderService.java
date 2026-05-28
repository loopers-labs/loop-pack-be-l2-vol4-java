package com.loopers.domain.order;

import com.loopers.domain.vo.ShippingInfo;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderModel create(UUID userId, ShippingInfo shippingInfo) {
        return orderRepository.save(new OrderModel(userId, shippingInfo));
    }

    public void addItem(OrderModel order, OrderItemModel item) {
        order.addItem(item);
    }

    public OrderModel get(UUID id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 주문을 찾을 수 없습니다."));
    }

    /** 소유자 일치 단건 조회 — 본인 주문 아니면 NOT_FOUND */
    public OrderModel getByIdAndUser(UUID id, UUID userId) {
        return orderRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    public Page<OrderModel> getListByUser(UUID userId, ZonedDateTime startAt, ZonedDateTime endAt, Pageable pageable) {
        return orderRepository.findAllByUserId(userId, startAt, endAt, pageable);
    }

    public Page<OrderModel> getList(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    public void confirm(OrderModel order, Long paymentAmount) {
        order.confirm(paymentAmount);
    }

    public void fail(OrderModel order) {
        order.fail();
    }

    public void cancel(OrderModel order) {
        order.cancel();
    }

    /** 스케줄러용 — 15분 초과 PENDING 주문 조회 */
    public List<OrderModel> findExpiredPending(ZonedDateTime before) {
        return orderRepository.findPendingBefore(before);
    }

    /** 스케줄러 배치용 — 아이템 fetch join 포함 (N+1 방지) */
    public List<OrderModel> findExpiredPendingWithItems(ZonedDateTime before) {
        return orderRepository.findPendingBeforeWithItems(before);
    }

    /** 스케줄러 배치 상태 변경 — 단일 UPDATE */
    public int failAllByIds(List<UUID> orderIds, ZonedDateTime now) {
        return orderRepository.failAllByIds(orderIds, now);
    }
}
