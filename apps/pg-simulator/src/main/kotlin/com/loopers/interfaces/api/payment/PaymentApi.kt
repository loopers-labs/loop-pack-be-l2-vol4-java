package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentApplicationService
import com.loopers.interfaces.api.ApiResponse
import com.loopers.domain.user.UserInfo
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentApi(
    private val paymentApplicationService: PaymentApplicationService,
    @Value("\${pg.slow-mode:false}") private val slowMode: Boolean,
) {

    @PostMapping
    fun request(
        userInfo: UserInfo,
        @RequestBody request: PaymentDto.PaymentRequest,
    ): ApiResponse<PaymentDto.TransactionResponse> {
        request.validate()

        if (slowMode) {
            // 느린 모드: 에러 없음, 150~300ms 지연 — slowCallDurationThreshold(100ms) 실험용
            // timelimiter(600ms)보다 낮아 타임아웃 없이 순수 slowCall만 발생
            Thread.sleep((150..300L).random())
        } else {
            // 기본 모드: 100~500ms 지연, 40% 에러
            Thread.sleep((100..500L).random())
            if ((1..100).random() <= 40) {
                throw CoreException(ErrorType.INTERNAL_ERROR, "현재 서버가 불안정합니다. 잠시 후 다시 시도해주세요.")
            }
        }

        return paymentApplicationService.createTransaction(request.toCommand(userInfo.userId))
            .let { PaymentDto.TransactionResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{transactionKey}")
    fun getTransaction(
        userInfo: UserInfo,
        @PathVariable("transactionKey") transactionKey: String,
    ): ApiResponse<PaymentDto.TransactionDetailResponse> {
        return paymentApplicationService.getTransactionDetailInfo(userInfo, transactionKey)
            .let { PaymentDto.TransactionDetailResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping
    fun getTransactionsByOrder(
        userInfo: UserInfo,
        @RequestParam("orderId", required = false) orderId: String,
    ): ApiResponse<PaymentDto.OrderResponse> {
        return paymentApplicationService.findTransactionsByOrderId(userInfo, orderId)
            .let { PaymentDto.OrderResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
