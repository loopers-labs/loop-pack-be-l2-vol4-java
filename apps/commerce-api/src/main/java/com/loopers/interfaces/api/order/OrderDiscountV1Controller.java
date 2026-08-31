package com.loopers.interfaces.api.order;
import com.loopers.application.order.*;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/orders")
public class OrderDiscountV1Controller {
 private final OrderDiscountFacade facade;
 @PostMapping("/{orderId}/discount") public ApiResponse<OrderDiscountInfo> apply(@PathVariable long orderId,@RequestHeader("X-USER-ID") long buyerId,@RequestBody Request r){return ApiResponse.success(facade.apply(orderId,buyerId,r.couponId(),Instant.now()));}
 public record Request(long couponId){}
}
