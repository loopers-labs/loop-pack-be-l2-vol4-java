package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller {

    private final PaymentFacade paymentFacade;

    @PostMapping
    public ApiResponse<PaymentV1Dto.PaymentResponse> pay(
            @LoginUser String loginId,
            @RequestBody PaymentV1Dto.PayRequest request
    ) {
        PaymentInfo info = paymentFacade.pay(loginId, request.toCommand());
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    /**
     * PG가 결제 확정(1~5초 후) 시 commerce에서 넘긴 callbackUrl 로 결과를 콜백 하는 수신부.
     * 콜백은 1회뿐이라 유실될 수 있으므로, PaymentRecoveryScheduler 폴링이 같은 reflect 를 호출한다.
     */
    @PostMapping("/callback")
    public ApiResponse<Object> callback(@RequestBody PaymentV1Dto.CallbackRequest request) {
        paymentFacade.reflect(
                Long.parseLong(request.orderId()),
                request.transactionKey(),
                request.status(),
                request.amount(),
                request.reason()
        );
        return ApiResponse.success();
    }
}
