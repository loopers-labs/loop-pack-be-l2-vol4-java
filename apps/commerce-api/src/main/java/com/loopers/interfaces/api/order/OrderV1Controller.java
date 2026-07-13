package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.queue.QueueApplicationService;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.queue.QueueRedisStore;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.AuthUser;
import com.loopers.interfaces.api.user.AuthUserContext;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller {

    private final OrderApplicationService orderApplicationService;
    private final QueueApplicationService queueApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
        @AuthUser AuthUserContext authUser,
        @Valid @RequestBody OrderV1Dto.CreateOrderRequest request
    ) {
        List<OrderItemCommand> commands = request.items().stream()
            .map(item -> new OrderItemCommand(item.productId(), item.quantity()))
            .toList();

        Set<Long> gatedProductIds = new LinkedHashSet<>();
        for (OrderV1Dto.OrderItemRequest item : request.items()) {
            if (queueApplicationService.isGated(item.productId())) {
                gatedProductIds.add(item.productId());
            }
        }

        if (gatedProductIds.isEmpty()) {
            OrderInfo info = orderApplicationService.createOrder(authUser.userId(), commands, request.couponId());
            return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
        }

        // 대기열 게이트 대상 상품이 있으면 주문 생성 전 토큰을 잠가 동시 중복 주문을 막는다.
        List<Long> lockedProductIds = new ArrayList<>();
        for (Long productId : gatedProductIds) {
            QueueRedisStore.TokenLockResult lockResult = queueApplicationService.lock(productId, authUser.userId());
            if (lockResult == QueueRedisStore.TokenLockResult.OK) {
                lockedProductIds.add(productId);
                continue;
            }
            unlockAll(lockedProductIds, authUser.userId());
            if (lockResult == QueueRedisStore.TokenLockResult.EXPIRED) {
                // 인증 자체는 이미 통과했으므로 401이 아니다 — 대기열 토큰이라는 별도 리소스가
                // 더 이상 유효하지 않다는 뜻이라 410(GONE)으로 응답한다.
                throw new CoreException(ErrorType.GONE, "대기열 토큰이 만료되었습니다. 다시 입장해주세요.");
            }
            throw new CoreException(ErrorType.CONFLICT, "이미 처리 중인 주문 요청입니다.");
        }

        try {
            OrderInfo info = orderApplicationService.createOrder(authUser.userId(), commands, request.couponId());
            return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
        } finally {
            unlockAll(lockedProductIds, authUser.userId());
        }
    }

    /** 잠갔던 토큰을 최선의 노력으로 되돌린다 — unlock 자체의 실패는 무시한다. */
    private void unlockAll(List<Long> productIds, Long userId) {
        for (Long productId : productIds) {
            try {
                queueApplicationService.unlock(productId, userId);
            } catch (Exception ignored) {
                // best-effort — unlock 실패는 토큰 TTL 만료로 자연 해소된다.
            }
        }
    }

    @GetMapping
    public ApiResponse<List<OrderV1Dto.OrderResponse>> getOrders(
        @AuthUser AuthUserContext authUser,
        @RequestParam(required = false) ZonedDateTime startAt,
        @RequestParam(required = false) ZonedDateTime endAt
    ) {
        ZonedDateTime resolvedStartAt = startAt != null ? startAt : ZonedDateTime.now().minusDays(30);
        ZonedDateTime resolvedEndAt = endAt != null ? endAt : ZonedDateTime.now();
        List<OrderInfo> infos = orderApplicationService.getOrders(authUser.userId(), resolvedStartAt, resolvedEndAt);
        List<OrderV1Dto.OrderResponse> responses = infos.stream()
            .map(OrderV1Dto.OrderResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
        @AuthUser AuthUserContext authUser,
        @PathVariable Long orderId
    ) {
        OrderInfo info = orderApplicationService.getOrder(authUser.userId(), orderId);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }
}
