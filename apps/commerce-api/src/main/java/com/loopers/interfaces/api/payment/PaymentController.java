package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.payment.PaymentFacade;
import com.loopers.application.payment.payment.PaymentResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.support.HeaderValidator;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentFacade paymentFacade;

    @PostMapping
    public ApiResponse<PaymentDto.PaymentResponse> requestPayment(
        @RequestHeader(HeaderValidator.LOGIN_ID) String loginId,
        @RequestHeader(HeaderValidator.LOGIN_PW) String loginPw,
        @RequestBody PaymentDto.PaymentRequest request
    ) {
        HeaderValidator.validateUser(loginId, loginPw);
        if (request == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 요청은 필수입니다.");
        }

        PaymentResult.Request result = paymentFacade.requestPayment(request.toCommand(loginId));
        return ApiResponse.success(PaymentDto.PaymentResponse.from(result));
    }

    @PostMapping("/callback")
    public ApiResponse<PaymentDto.PaymentResponse> callback(@RequestBody PaymentDto.PaymentCallbackRequest request) {
        if (request == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 콜백 요청은 필수입니다.");
        }

        PaymentResult.Request result = paymentFacade.handleCallback(request.toCommand());
        return ApiResponse.success(PaymentDto.PaymentResponse.from(result));
    }

    @PostMapping("/{orderId}/sync")
    public ApiResponse<PaymentDto.PaymentResponse> syncPayment(
        @RequestHeader(HeaderValidator.LOGIN_ID) String loginId,
        @RequestHeader(HeaderValidator.LOGIN_PW) String loginPw,
        @PathVariable Long orderId
    ) {
        HeaderValidator.validateUser(loginId, loginPw);
        PaymentResult.Request result = paymentFacade.syncPayment(loginId, orderId);
        return ApiResponse.success(PaymentDto.PaymentResponse.from(result));
    }
}
