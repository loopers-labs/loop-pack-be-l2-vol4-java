package com.loopers.application.order;

import com.loopers.application.product.ProductService;
import com.loopers.application.stock.StockService;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final StockService stockService;
    private final OrderDomainService orderDomainService;

    @Transactional(readOnly = true)
    public long calculateTotalPrice(List<OrderItemCommand> commands) {
        Map<Long, ProductModel> productMap = commands.stream()
            .collect(Collectors.toMap(
                OrderItemCommand::productId,
                cmd -> productService.getById(cmd.productId())
            ));
        Map<Long, Integer> quantityMap = commands.stream()
            .collect(Collectors.toMap(OrderItemCommand::productId, OrderItemCommand::quantity));
        return orderDomainService.calculateTotalPrice(List.copyOf(productMap.values()), quantityMap);
    }

    @Transactional
    public OrderModel create(Long memberId, List<OrderItemCommand> commands, Long couponId, long originalAmount, long discountAmount) {
        // 1. 상품 존재 확인
        Map<Long, ProductModel> productMap = commands.stream()
            .collect(Collectors.toMap(
                OrderItemCommand::productId,
                cmd -> productService.getById(cmd.productId())
            ));

        // 2. productId 오름차순 정렬 (데드락 방지)
        List<OrderItemCommand> sorted = commands.stream()
            .sorted(Comparator.comparingLong(OrderItemCommand::productId))
            .toList();

        // 3. 재고 비관적 락 취득
        Map<Long, StockModel> stockMap = sorted.stream()
            .collect(Collectors.toMap(
                OrderItemCommand::productId,
                cmd -> stockService.getByProductIdForUpdate(cmd.productId())
            ));

        Map<Long, Integer> quantityMap = commands.stream()
            .collect(Collectors.toMap(OrderItemCommand::productId, OrderItemCommand::quantity));

        // 4. 재고 검증 (도메인 서비스)
        orderDomainService.validateStockAvailability(List.copyOf(stockMap.values()), quantityMap);

        // 5. 주문 저장
        OrderModel order = orderRepository.save(new OrderModel(memberId, couponId, originalAmount, discountAmount));

        // 6. OrderItem 저장 (스냅샷)
        for (OrderItemCommand cmd : commands) {
            ProductModel product = productMap.get(cmd.productId());
            orderRepository.saveItem(new OrderItemModel(order.getId(), product.getId(), product.getName(), product.getPrice(), cmd.quantity()));
        }

        // 7. 재고 차감 (이미 락된 StockModel 재사용)
        for (OrderItemCommand cmd : sorted) {
            stockMap.get(cmd.productId()).decrease(cmd.quantity());
        }

        return order;
    }

    @Transactional
    public void cancel(Long orderId, Long memberId) {
        OrderModel order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 주문을 찾을 수 없습니다."));

        if (!order.isOwnedBy(memberId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 주문만 취소할 수 있습니다.");
        }

        order.cancel();

        // orderItems productId 오름차순 정렬 (데드락 방지)
        List<OrderItemModel> items = orderRepository.findItemsByOrderId(orderId).stream()
            .sorted(Comparator.comparingLong(OrderItemModel::getProductId))
            .toList();

        for (OrderItemModel item : items) {
            StockModel stock = stockService.getByProductIdForUpdate(item.getProductId());
            stock.increase(item.getQuantity());
        }
    }

    @Transactional
    public void confirmBySystem(Long orderId) {
        OrderModel order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 주문을 찾을 수 없습니다."));
        order.confirm();
    }

    @Transactional
    public void cancelBySystem(Long orderId) {
        OrderModel order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 주문을 찾을 수 없습니다."));

        order.cancel();

        List<OrderItemModel> items = orderRepository.findItemsByOrderId(orderId).stream()
            .sorted(Comparator.comparingLong(OrderItemModel::getProductId))
            .toList();

        for (OrderItemModel item : items) {
            StockModel stock = stockService.getByProductIdForUpdate(item.getProductId());
            stock.increase(item.getQuantity());
        }
    }

    @Transactional
    public OrderModel confirm(Long orderId, Long memberId) {
        OrderModel order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 주문을 찾을 수 없습니다."));

        if (!order.isOwnedBy(memberId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 주문만 확정할 수 있습니다.");
        }

        order.confirm();
        return order;
    }

    @Transactional(readOnly = true)
    public List<OrderModel> getOrders(Long memberId, LocalDate startAt, LocalDate endAt) {
        return orderRepository.findAllByMemberIdAndDateRange(memberId, startAt, endAt);
    }

    @Transactional(readOnly = true)
    public OrderModel getById(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<OrderModel> getAllOrders(LocalDate startAt, LocalDate endAt) {
        return orderRepository.findAllByDateRange(startAt, endAt);
    }

    @Transactional(readOnly = true)
    public List<OrderItemModel> getItemsByOrderId(Long orderId) {
        return orderRepository.findItemsByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public List<OrderItemModel> getItemsByOrderIds(List<Long> orderIds) {
        return orderRepository.findItemsByOrderIdIn(orderIds);
    }
}
