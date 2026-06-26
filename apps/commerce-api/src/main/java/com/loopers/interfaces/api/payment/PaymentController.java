package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentFacade paymentFacade;
    private final PaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentDto.PaymentResponse> requestPayment(
        @Valid @RequestBody PaymentDto.CreateRequest request,
        @RequestAttribute("userId") Long userId
    ) {
        PaymentModel payment = paymentFacade.requestPayment(
            userId,
            request.orderId(),
            CardType.valueOf(request.cardType()),
            request.cardNo(),
            request.amount()
        );
        return ApiResponse.success(PaymentDto.PaymentResponse.from(payment));
    }

    @PostMapping("/callback")
    public ApiResponse<Void> handleCallback(
        @RequestBody PaymentDto.CallbackRequest request
    ) {
        paymentFacade.handleCallback(request.transactionKey(), request.status(), request.reason());
        return ApiResponse.success(null);
    }

    @GetMapping("/{paymentId}")
    public ApiResponse<PaymentDto.PaymentResponse> getPayment(
        @PathVariable Long paymentId,
        @RequestAttribute("userId") Long userId
    ) {
        PaymentModel payment = paymentService.getById(paymentId);
        return ApiResponse.success(PaymentDto.PaymentResponse.from(payment));
    }
}
