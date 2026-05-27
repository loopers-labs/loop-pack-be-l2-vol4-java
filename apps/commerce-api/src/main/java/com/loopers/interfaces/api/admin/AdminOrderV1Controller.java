package com.loopers.interfaces.api.admin;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.domain.order.OrderStatus;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.OrderV1Dto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 주문 모니터링 (UC-12, Admin). 운영자는 전체 사용자의 주문을 상태 필터·페이지로 조회한다.
 * 본인 격리 규칙(§7.4)은 대고객 전용이라 여기에는 적용하지 않는다.
 *
 * NOTE: 현재 프로젝트에 운영자 권한 체계가 없어 다른 Admin 기능(브랜드·상품 관리)과 동일하게
 *       인증 가드 없이 노출된다. 운영 환경에서는 운영자 인증·인가 가드가 선행되어야 한다.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderV1Controller {

    private final OrderFacade orderFacade;

    @GetMapping
    public ApiResponse<List<OrderV1Dto.OrderResponse>> getOrders(
        @RequestParam(value = "status", required = false) OrderStatus status,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        List<OrderV1Dto.OrderResponse> responses = orderFacade.getOrders(status, page, size).stream()
            .map(OrderV1Dto.OrderResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
