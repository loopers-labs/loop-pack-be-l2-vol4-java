package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthHeaders;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller {

    private final OrderFacade orderFacade;
    private final UserFacade userFacade;

    @PostMapping
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
        @RequestHeader(AuthHeaders.LOGIN_ID) String loginId,
        @RequestHeader(AuthHeaders.LOGIN_PW) String loginPw,
        @RequestBody OrderV1Dto.CreateOrderRequest request
    ) {
        UserInfo user = userFacade.login(loginId, loginPw);
        List<OrderFacade.OrderItemCommand> commands = request.items().stream()
            .map(item -> new OrderFacade.OrderItemCommand(item.productId(), item.quantity()))
            .toList();
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(orderFacade.createOrder(user.id(), commands)));
    }

    @GetMapping
    public ApiResponse<List<OrderV1Dto.OrderResponse>> getOrders(
        @RequestHeader(AuthHeaders.LOGIN_ID) String loginId,
        @RequestHeader(AuthHeaders.LOGIN_PW) String loginPw,
        @RequestParam(required = false) String startAt,
        @RequestParam(required = false) String endAt
    ) {
        UserInfo user = userFacade.login(loginId, loginPw);
        return ApiResponse.success(orderFacade.getOrders(user.id(), startAt, endAt).stream()
            .map(OrderV1Dto.OrderResponse::from)
            .toList());
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
        @RequestHeader(AuthHeaders.LOGIN_ID) String loginId,
        @RequestHeader(AuthHeaders.LOGIN_PW) String loginPw,
        @PathVariable Long orderId
    ) {
        UserInfo user = userFacade.login(loginId, loginPw);
        OrderInfo order = orderFacade.getOrder(orderId);
        if (!order.userId().equals(user.id())) {
            throw new CoreException(ErrorType.FORBIDDEN, "다른 유저의 주문에 접근할 수 없습니다.");
        }
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(order));
    }
}
