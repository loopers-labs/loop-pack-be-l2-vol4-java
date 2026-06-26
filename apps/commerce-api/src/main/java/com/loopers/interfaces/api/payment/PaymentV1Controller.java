package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentCallbackHandler;
import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller {

    private final PaymentFacade paymentFacade;
    private final PaymentCallbackHandler paymentCallbackHandler;

    /**
     * 결제 요청. 응답은 "처리 중(PROCESSING)" 으로 빠르게 돌려주고, 최종 결과는 콜백/폴링으로 확정된다 (C안 모델).
     */
    @PostMapping
    public ApiResponse<PaymentV1Dto.PaymentResponse> request(
        @LoginUser UserInfo loginUser,
        @Valid @RequestBody PaymentV1Dto.PaymentRequest request
    ) {
        PaymentInfo info = paymentFacade.requestPayment(loginUser.id(), request.toCommand());
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    /**
     * PG 가 비동기 결제 결과를 통보하는 콜백 엔드포인트.
     * PG 재시도 차단을 위해 어떤 입력에도 200 OK 로 응답한다 (실제 처리는 멱등 보장 핸들러가 수행).
     */
    @PostMapping("/callback")
    public ApiResponse<Object> callback(@RequestBody PaymentCallbackV1Dto callback) {
        paymentCallbackHandler.handle(callback);
        return ApiResponse.success();
    }
}
