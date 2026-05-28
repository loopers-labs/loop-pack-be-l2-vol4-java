package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
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
import java.util.UUID;

@RequiredArgsConstructor
@Component
@Transactional(readOnly = true)
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;
    private final StockService stockService;

    /** 주문 생성 — 상품 유효성 검증 + 재고 예약 + 주문 저장
     *
     * 순서 주의:
     *  1단계: 상품 조회 → 아이템 생성 → order에 addItem (pgAmount 누적)
     *  2단계: 재고 예약 (첫 번째 reserve의 flushAutomatically=true가 order+items를 DB에 flush)
     *  3단계: order 재조회 (1차 캐시 clear로 order가 detach됐으므로 fresh 로드)
     *
     * reserve()의 clearAutomatically=true가 1차 캐시 전체를 클리어하므로,
     * 모든 addItem을 reserve() 전에 완료해야 pgAmount와 items가 올바르게 DB에 저장된다.
     */
    @Transactional
    public OrderInfo create(UUID userId, ShippingInfo shippingInfo, List<OrderItemRequest> itemRequests) {
        OrderModel order = orderService.create(userId, shippingInfo);

        // 1단계: 상품 조회 + 아이템 생성 + order에 추가 (reserve 전에 모두 완료)
        List<OrderItemRequest> resolved = itemRequests.stream().map(req -> {
            ProductModel product = productService.getActive(req.productId());
            // brand()는 lazy — reserve() 호출 전에 미리 로드
            String productName = product.getName();
            String brandName   = product.getBrand().getName();
            Long price         = product.getPrice();
            OrderItemModel item = new OrderItemModel(req.productId(), productName, brandName, price, req.quantity());
            orderService.addItem(order, item);
            return req;
        }).toList();

        // 2단계: 재고 예약 (첫 번째 reserve의 flush가 order+items를 DB에 저장)
        resolved.forEach(req -> stockService.reserve(req.productId(), req.quantity()));

        // 3단계: clear로 detach된 order를 재조회하여 반환 (items lazy load 포함)
        return OrderInfo.from(orderService.get(order.getId()));
    }

    /** 주문 단건 조회 — 본인 주문만 허용 */
    public OrderInfo get(UUID orderId, UserModel user) {
        OrderModel order = orderService.get(orderId);
        if (!order.getUserId().equals(user.getId())) {
            throw new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.");
        }
        return OrderInfo.from(order);
    }

    /** 주문 목록 조회 — 본인 주문만 허용, 날짜 범위 필터 */
    public Page<OrderInfo> getListByUser(UUID userId, UserModel user, ZonedDateTime startAt, ZonedDateTime endAt, Pageable pageable) {
        if (!userId.equals(user.getId())) {
            throw new CoreException(ErrorType.NOT_FOUND, "주문 목록을 조회할 수 없습니다.");
        }
        return orderService.getListByUser(userId, startAt, endAt, pageable).map(OrderInfo::from);
    }

    /** 어드민 주문 목록 조회 — 전체 */
    public Page<OrderInfo> getList(Pageable pageable) {
        return orderService.getList(pageable).map(OrderInfo::from);
    }

    /** 주문 취소 — CONFIRMED 상태만, 재고 복구 포함 */
    @Transactional
    public OrderInfo cancel(UUID orderId, UserModel user) {
        OrderModel order = orderService.get(orderId);
        if (!order.getUserId().equals(user.getId())) {
            throw new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.");
        }
        order.getItems().forEach(item -> stockService.restore(item.getProductId(), item.getQuantity()));
        orderService.cancel(order);
        return OrderInfo.from(order);
    }

    /** 스케줄러용 — 만료된 PENDING 주문 일괄 실패 처리 + 재고 해제 */
    @Transactional
    public void expirePendingOrders(ZonedDateTime before) {
        List<OrderModel> expired = orderService.findExpiredPending(before);
        for (OrderModel order : expired) {
            order.getItems().forEach(item -> stockService.release(item.getProductId(), item.getQuantity()));
            orderService.fail(order);
        }
    }

    public record OrderItemRequest(UUID productId, int quantity) {}
}
