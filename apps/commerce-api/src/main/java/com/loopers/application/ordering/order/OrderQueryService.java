package com.loopers.application.ordering.order;

import com.loopers.domain.ordering.order.Order;
import com.loopers.domain.ordering.order.OrderRepository;
import com.loopers.domain.payment.payment.Payment;
import com.loopers.domain.payment.payment.PaymentRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public OrderResult.Detail getOrder(String userId, Long orderId) {
        validateUserId(userId);
        validateOrderId(orderId);

        Order order = orderRepository.findByIdAndUserId(orderId, userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
        Payment payment = getPayment(order.getId());

        return OrderResult.Detail.from(order, payment);
    }

    @Transactional(readOnly = true)
    public List<OrderResult.Summary> getOrders(OrderQuery.ListOrders query) {
        validateListQuery(query);

        ZonedDateTime startAt = query.startAt().atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime endExclusive = query.endAt().plusDays(1).atStartOfDay(ZoneId.systemDefault());
        List<Order> orders = orderRepository.findByUserIdAndCreatedAtBetween(query.userId(), startAt, endExclusive);
        Map<Long, Payment> payments = paymentRepository.findAllByOrderIds(
                orders.stream().map(Order::getId).toList()
            )
            .stream()
            .collect(Collectors.toMap(Payment::getOrderId, Function.identity()));

        return orders.stream()
            .map(order -> OrderResult.Summary.from(order, getPaymentFrom(payments, order.getId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public PageResult<OrderResult.Summary> getAdminOrders(int page, int size) {
        validatePage(page, size);

        long totalElements = orderRepository.countAll();
        List<Order> orders = orderRepository.findAllForAdmin(page, size);
        Map<Long, Payment> payments = paymentRepository.findAllByOrderIds(
                orders.stream().map(Order::getId).toList()
            )
            .stream()
            .collect(Collectors.toMap(Payment::getOrderId, Function.identity()));

        List<OrderResult.Summary> items = orders.stream()
            .map(order -> OrderResult.Summary.from(order, getPaymentFrom(payments, order.getId())))
            .toList();

        return PageResult.of(items, page, size, totalElements);
    }

    @Transactional(readOnly = true)
    public OrderResult.Detail getAdminOrder(Long orderId) {
        validateOrderId(orderId);

        Order order = orderRepository.find(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
        Payment payment = getPayment(order.getId());

        return OrderResult.Detail.from(order, payment);
    }

    private Payment getPayment(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR, "[orderId = " + orderId + "] 결제 정보를 찾을 수 없습니다."));
    }

    private Payment getPaymentFrom(Map<Long, Payment> payments, Long orderId) {
        Payment payment = payments.get(orderId);
        if (payment == null) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "[orderId = " + orderId + "] 결제 정보를 찾을 수 없습니다.");
        }

        return payment;
    }

    private void validateListQuery(OrderQuery.ListOrders query) {
        if (query == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 목록 조회 조건은 필수입니다.");
        }
        validateUserId(query.userId());
        validateDate(query.startAt(), "startAt");
        validateDate(query.endAt(), "endAt");
        if (query.startAt().isAfter(query.endAt())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "startAt은 endAt보다 늦을 수 없습니다.");
        }
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
    }

    private void validateOrderId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다.");
        }
        if (size <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 1 이상이어야 합니다.");
        }
    }

    private void validateDate(LocalDate value, String name) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, name + "은 필수입니다.");
        }
    }
}
