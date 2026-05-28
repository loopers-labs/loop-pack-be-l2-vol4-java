package com.loopers.order.interfaces.api;

import com.loopers.common.interfaces.api.ApiResponse;
import com.loopers.member.application.MemberFacade;
import com.loopers.order.application.OrderFacade;
import com.loopers.order.application.OrderInfo;
import com.loopers.order.domain.OrderLine;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderFacade orderFacade;
    private final MemberFacade memberFacade;

    @PostMapping
    public ApiResponse<OrderResponse> createOrder(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @RequestBody CreateOrderRequest request) {
        Long memberId = memberFacade.authenticate(loginId, loginPw);

        List<OrderLine> lines =
            request.items().stream()
                .map(item -> new OrderLine(item.productId(), item.quantity() == null ? 0 : item.quantity()))
                .toList();

        OrderInfo info = orderFacade.createOrder(memberId, lines);
        return ApiResponse.success(OrderResponse.from(info));
    }

    @GetMapping
    public ApiResponse<List<OrderResponse>> getMyOrders(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @RequestParam(value = "startAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startAt,
        @RequestParam(value = "endAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endAt) {
        Long memberId = memberFacade.authenticate(loginId, loginPw);
        List<OrderInfo> infos = orderFacade.getMyOrders(memberId, startAt, endAt);
        return ApiResponse.success(infos.stream().map(OrderResponse::from).toList());
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getMyOrder(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @PathVariable("orderId") Long orderId) {
        Long memberId = memberFacade.authenticate(loginId, loginPw);
        OrderInfo info = orderFacade.getMyOrder(memberId, orderId);
        return ApiResponse.success(OrderResponse.from(info));
    }
}
