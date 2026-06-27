package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller {

    private final PaymentFacade paymentFacade;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
        @LoginUser User user,
        @RequestBody PaymentV1Dto.PaymentRequest request
    ) {
        PaymentInfo.Create info = paymentFacade.requestPayment(request.toCommand(user.getId()));
        return ApiResponse.success(new PaymentV1Dto.PaymentResponse(info.transactionKey()));
    }

    @PostMapping("/callback")
    public ApiResponse<Void> receiveCallback(
        @RequestBody PaymentV1Dto.CallbackRequest request
    ) {
        paymentFacade.receiveCallback(request.toCommand());
        return ApiResponse.success(null);
    }
}