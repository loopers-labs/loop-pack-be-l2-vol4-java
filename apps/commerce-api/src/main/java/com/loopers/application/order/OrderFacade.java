package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStockService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.vo.ShippingInfo;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
@Transactional(readOnly = true)
public class OrderFacade {

    private final OrderService orderService;
    private final OrderStockService orderStockService;
    private final ProductService productService;
    private final StockService stockService;

    /** 주문 생성 — 상품 유효성 검증 + 재고 예약 + 주문 저장 */
    @Transactional
    public OrderInfo create(UUID userId, ShippingInfo shippingInfo, List<OrderItemRequest> itemRequests) {
        OrderModel order = orderService.create(userId, shippingInfo);

        // 1단계: 상품 검증 + 아이템 추가
        // (brand lazy load를 reserve() 전에 완료 — reserve의 clearAutomatically가 1LC 클리어하기 때문)
        for (OrderItemRequest req : itemRequests) {
            ProductModel product = productService.getActive(req.productId());
            orderService.addItem(order, new OrderItemModel(
                req.productId(),
                product.getName(),
                product.getBrand().getName(),
                product.getPrice(),
                req.quantity()
            ));
        }

        // 2단계: 재고 예약
        // (flushAutomatically가 order+items를 DB에 저장, clearAutomatically가 1LC 클리어)
        itemRequests.forEach(req -> stockService.reserve(req.productId(), req.quantity()));

        // 3단계: 1LC에서 detach된 order 재조회
        return OrderInfo.from(orderService.get(order.getId()));
    }

    /** 주문 단건 조회 — 본인 주문만 허용 */
    public OrderInfo get(UUID orderId, UserModel user) {
        return OrderInfo.from(orderService.getByIdAndUser(orderId, user.getId()));
    }

    /** 주문 목록 조회 — 본인 주문만 허용, 날짜 범위 필터 */
    public Page<OrderInfo> getListByUser(UUID userId, UserModel user, ZonedDateTime startAt, ZonedDateTime endAt, Pageable pageable) {
        if (!userId.equals(user.getId())) {
            throw new CoreException(ErrorType.NOT_FOUND, "주문 목록을 조회할 수 없습니다.");
        }
        return orderService.getListByUser(userId, startAt, endAt, pageable).map(OrderInfo::from);
    }

    /** 어드민 주문 단건 조회 — 소유권 무관 */
    public OrderInfo getById(UUID orderId) {
        return OrderInfo.from(orderService.get(orderId));
    }

    /** 어드민 주문 목록 조회 — 전체 */
    public Page<OrderInfo> getList(Pageable pageable) {
        return orderService.getList(pageable).map(OrderInfo::from);
    }

    /** 주문 취소 — CONFIRMED 상태만, 재고 복구 포함 */
    @Transactional
    public OrderInfo cancel(UUID orderId, UserModel user) {
        OrderModel order = orderService.getByIdAndUser(orderId, user.getId());
        orderStockService.cancelOrder(order);
        return OrderInfo.from(order);
    }

    /** 스케줄러용 — 만료된 PENDING 주문 일괄 실패 처리 + 재고 해제 (배치) */
    @Transactional
    public void expirePendingOrders(ZonedDateTime before) {
        List<OrderModel> expiredOrders = orderService.findExpiredPendingWithItems(before);
        if (expiredOrders.isEmpty()) return;

        // 1단계: productId별 해제 수량 집계
        Map<UUID, Integer> releaseMap = expiredOrders.stream()
            .flatMap(o -> o.getItems().stream())
            .collect(Collectors.toMap(
                item -> item.getProductId(),
                item -> item.getQuantity(),
                Integer::sum
            ));

        // 2단계: 재고 배치 해제 (원자적 relative UPDATE, SELECT FOR UPDATE 불필요)
        stockService.releaseAll(releaseMap);

        // 3단계: 주문 상태 일괄 FAILED 처리
        List<UUID> orderIds = expiredOrders.stream().map(OrderModel::getId).toList();
        orderService.failAllByIds(orderIds, before);
    }

    public record OrderItemRequest(UUID productId, int quantity) {}
}
