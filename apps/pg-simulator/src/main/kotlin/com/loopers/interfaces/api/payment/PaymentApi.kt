package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentApplicationService
import com.loopers.interfaces.api.ApiResponse
import com.loopers.domain.user.UserInfo
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
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
) {

    // 1. 결제 요청 (transactionKey 발급 -> PENDING 저장 -> 이벤트 발행해 비동기 승인 처리 시작. 응답값으로 transactionKey, status, reason 반환 )
    @PostMapping
    fun request(
        userInfo: UserInfo,
        @RequestBody request: PaymentDto.PaymentRequest,
    ): ApiResponse<PaymentDto.TransactionResponse> {
        request.validate()

        // 100ms ~ 500ms 지연
        Thread.sleep((100..500L).random())

        // 40% 확률로 요청 실패
        if ((1..100).random() <= 40) {
            throw CoreException(ErrorType.INTERNAL_ERROR, "현재 서버가 불안정합니다. 잠시 후 다시 시도해주세요.")
        }

        return paymentApplicationService.createTransaction(request.toCommand(userInfo.userId))
            .let { PaymentDto.TransactionResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    // 2. 결제 단건 조회 (transactionKey로 현재 status 확인.  PENDING/SUCCESS/FAILED )
    @GetMapping("/{transactionKey}")
    fun getTransaction(
        userInfo: UserInfo,
        @PathVariable("transactionKey") transactionKey: String,
    ): ApiResponse<PaymentDto.TransactionDetailResponse> {
        return paymentApplicationService.getTransactionDetailInfo(userInfo, transactionKey)
            .let { PaymentDto.TransactionDetailResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    // 3. 주문별 결제 조회. (하나의 주문 orderId에 묶인 결제 트랜잭션 리스트 조회)
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
