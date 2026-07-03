package com.loopers.interfaces.api.coupon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.event.coupon.CouponIssueRequestEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class CouponController {

    private final CouponFacade couponFacade;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/api/v1/coupons/{templateId}/issue")
    public ApiResponse<Void> issue(
        @PathVariable Long templateId,
        @RequestAttribute("userId") Long userId
    ) {
        var template = couponFacade.validateTemplate(templateId);
        try {
            String payload = objectMapper.writeValueAsString(new CouponIssueRequestEvent(userId, templateId));
            String partitionKey = template.getTotalCount() != null ? String.valueOf(templateId) : null;
            kafkaTemplate.send("coupon-issue-requests", partitionKey, payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("쿠폰 발급 요청 직렬화 실패", e);
        }
        return ApiResponse.success(null);
    }

    @GetMapping("/api/v1/users/me/coupons")
    public ApiResponse<List<CouponDto.MyCouponResponse>> getMyCoupons(
        @RequestAttribute("userId") Long userId
    ) {
        var coupons = couponFacade.getMyCoupons(userId).stream()
            .map(CouponDto.MyCouponResponse::from)
            .toList();
        return ApiResponse.success(coupons);
    }
}
