package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller implements OrderV1ApiSpec {

    private final OrderFacade orderFacade;

    @PostMapping
    @Override
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
            @RequestHeader(value = "X-Loopers-UserId", required = false) Long userId,
            @RequestBody OrderV1Dto.OrderRequest request
    ) {
        // ① 인증 식별자 확인 (헤더 필수)
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "X-Loopers-UserId 헤더가 필요합니다.");
        }

        // ② 헤더 userId + 바디 request 를 합쳐 응용 입력(OrderCriteria)으로 변환 → Facade 호출
        OrderInfo info = orderFacade.placeOrder(request.toCriteria(userId));

        // ③ 응용 출력(OrderInfo) → API 응답(OrderResponse) 으로 변환
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }
}
