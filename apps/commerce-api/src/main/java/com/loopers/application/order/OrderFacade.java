package com.loopers.application.order;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStockService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockService;
import com.loopers.domain.user.UserModel;
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
import java.util.Optional;
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
    private final UserCouponService userCouponService;

    /** 주문 생성 — 멱등 키 pre-check 후 상품 유효성 검증 + 쿠폰 적용 + 재고 예약 + 주문 저장 (단일 트랜잭션) */
    @Transactional
    public OrderInfo create(UUID userId, List<OrderItemRequest> itemRequests, UUID couponId,
                            String receiverName, String receiverPhone, String zipCode, String address, String detailAddress,
                            String idempotencyKey) {
        Optional<OrderModel> existing = orderService.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return OrderInfo.from(existing.get());
        }
        OrderModel order = orderService.create(userId, idempotencyKey, receiverName, receiverPhone, zipCode, address, detailAddress);

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

        // 2단계: 쿠폰 적용 — 재고 예약(1LC clear) 전에 order에 할인 반영
        if (couponId != null) {
            applyCoupon(order, userId, couponId);
        }

        // 3단계: 재고 예약
        // (flushAutomatically가 order+items를 DB에 저장, clearAutomatically가 1LC 클리어)
        itemRequests.forEach(req -> stockService.reserve(req.productId(), req.quantity()));

        // 4단계: 1LC에서 detach된 order 재조회
        return OrderInfo.from(orderService.get(order.getId()));
    }

    /** 쿠폰 검증(소유/만료/최소금액) → 사용(조건부 UPDATE) → 할인 반영. 재고예약과 동일 트랜잭션이라 실패 시 함께 롤백 */
    private void applyCoupon(OrderModel order, UUID userId, UUID couponId) {
        UserCouponModel coupon = userCouponService.getOwned(couponId, userId);
        long original = order.getOriginalAmount();
        if (coupon.isExpired(ZonedDateTime.now())) {
            throw new CoreException(ErrorType.CONFLICT, "만료된 쿠폰입니다.");
        }
        if (!coupon.meetsMinOrderAmount(original)) {
            throw new CoreException(ErrorType.CONFLICT, "최소 주문 금액을 충족하지 않습니다.");
        }
        userCouponService.use(couponId, order.getId());
        order.applyCoupon(couponId, coupon.calculateDiscount(original));
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

    /** 주문 취소 — CONFIRMED 상태만, 재고 복구 + 쿠폰 복구. 주문 행 비관적 락으로 전이 직렬화 */
    @Transactional
    public OrderInfo cancel(UUID orderId, UserModel user) {
        OrderModel order = orderService.getByIdAndUserForUpdate(orderId, user.getId());
        orderStockService.cancelOrder(order); // CANCELLED 전이 + 재고 복구 (CONFIRMED 아니면 예외)
        if (order.getCouponId() != null) {
            userCouponService.releaseByOrderId(orderId); // 적용 쿠폰 USED → AVAILABLE
        }
        return OrderInfo.from(order);
    }

    /** 스케줄러용 — 만료된 PENDING 주문 일괄 실패 처리 + 재고/쿠폰 복구 (배치) */
    @Transactional
    public void expirePendingOrders(ZonedDateTime before) {
        // 대상 주문을 비관적 락으로 조회 — 락 보유 동안 confirm/fail이 끼어들지 못하므로
        // 이 집합이 곧 FAILED 전이 승자집합 (confirm된 주문은 PENDING이 아니라 애초에 제외됨)
        List<OrderModel> expiredOrders = orderService.findExpiredPendingForUpdate(before);
        if (expiredOrders.isEmpty()) return;

        // 1단계: productId별 해제 수량 집계 (items lazy 로드)
        Map<UUID, Integer> releaseMap = expiredOrders.stream()
            .flatMap(o -> o.getItems().stream())
            .collect(Collectors.toMap(
                item -> item.getProductId(),
                item -> item.getQuantity(),
                Integer::sum
            ));

        // 2단계: 재고 배치 해제
        stockService.releaseAll(releaseMap);

        // 3단계: 만료 주문에 적용된 쿠폰 일괄 복구 (USED → AVAILABLE, 없으면 no-op)
        List<UUID> orderIds = expiredOrders.stream().map(OrderModel::getId).toList();
        userCouponService.releaseByOrderIds(orderIds);

        // 4단계: 주문 상태 일괄 FAILED 처리
        orderService.failAllByIds(orderIds, before);
    }

}
