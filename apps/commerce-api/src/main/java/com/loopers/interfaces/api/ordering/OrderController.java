package com.loopers.interfaces.api.ordering;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.application.ordering.order.OrderFacade;
import com.loopers.application.ordering.order.OrderQuery;
import com.loopers.application.ordering.order.OrderQueryService;
import com.loopers.application.ordering.order.OrderResult;
import com.loopers.interfaces.api.support.HeaderValidator;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderFacade orderFacade;
    private final OrderQueryService orderQueryService;

    @PostMapping
    public ApiResponse<OrderDto.OrderCreateResponse> placeOrder(
        @RequestHeader(HeaderValidator.LOGIN_ID) String loginId,
        @RequestHeader(HeaderValidator.LOGIN_PW) String loginPw,
        @RequestBody OrderDto.OrderCreateRequest request
    ) {
        HeaderValidator.validateUser(loginId, loginPw);
        if (request == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 요청은 필수입니다.");
        }
        OrderResult.Detail result = orderFacade.placeOrder(request.toCommand(loginId));
        return ApiResponse.success(OrderDto.OrderCreateResponse.from(result));
    }

    @GetMapping
    public ApiResponse<List<OrderDto.OrderListItemResponse>> getOrders(
        @RequestHeader(HeaderValidator.LOGIN_ID) String loginId,
        @RequestHeader(HeaderValidator.LOGIN_PW) String loginPw,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt
    ) {
        HeaderValidator.validateUser(loginId, loginPw);
        List<OrderDto.OrderListItemResponse> responses = orderQueryService.getOrders(
                new OrderQuery.ListOrders(loginId, startAt, endAt)
            )
            .stream()
            .map(OrderDto.OrderListItemResponse::from)
            .toList();

        return ApiResponse.success(responses);
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDto.OrderDetailResponse> getOrder(
        @RequestHeader(HeaderValidator.LOGIN_ID) String loginId,
        @RequestHeader(HeaderValidator.LOGIN_PW) String loginPw,
        @PathVariable Long orderId
    ) {
        HeaderValidator.validateUser(loginId, loginPw);
        OrderResult.Detail result = orderQueryService.getOrder(loginId, orderId);
        return ApiResponse.success(OrderDto.OrderDetailResponse.from(result));
    }
}
