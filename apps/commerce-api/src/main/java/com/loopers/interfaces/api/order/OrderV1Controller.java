package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.user.UserFacade;
import com.loopers.domain.order.OrderLine;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 주문 — 사용자 식별은 X-Loopers-LoginId/LoginPw 헤더 인증으로 처리.
 * 조회는 본인 주문만 노출하며, 타인 주문은 NOT_FOUND로 통일 응대한다(01 §7.4 정보 노출 방지).
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller {

    private final OrderFacade orderFacade;
    private final UserFacade userFacade;

    @PostMapping
    public ApiResponse<OrderV1Dto.OrderResponse> placeOrder(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @RequestBody OrderV1Dto.PlaceOrderRequest request
    ) {
        Long userId = userFacade.authenticate(loginId, loginPw);
        List<OrderLine> lines = request.items().stream()
            .map(item -> new OrderLine(item.productId(), item.quantity()))
            .toList();
        OrderInfo info = orderFacade.placeOrder(userId, request.paymentMethod(), lines);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
        @PathVariable(value = "orderId") Long orderId,
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        Long userId = userFacade.authenticate(loginId, loginPw);
        OrderInfo info = orderFacade.getOrder(orderId);
        if (!info.userId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다.");
        }
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }
}
