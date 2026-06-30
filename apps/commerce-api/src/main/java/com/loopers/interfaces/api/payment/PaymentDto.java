package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentCallbackCommand;
import com.loopers.application.payment.PaymentGatewayCommand;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.application.payment.PaymentOrderIdMapper;
import com.loopers.domain.payment.PaymentCardType;
import com.loopers.domain.payment.PaymentGatewayStatus;
import com.loopers.domain.payment.PaymentPendingReason;
import com.loopers.domain.payment.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class PaymentDto {

    private PaymentDto() {}

    public static final class RequestPayment {

        private RequestPayment() {}

        public static final class V1 {

            private V1() {}

            public record Request(
                @NotNull
                Long orderId,
                @NotNull
                PaymentCardType cardType,
                @NotBlank
                @Pattern(regexp = "^\\d{4}-\\d{4}-\\d{4}-\\d{4}$")
                String cardNo
            ) {
                public PaymentGatewayCommand toCommand(String userLoginId) {
                    return new PaymentGatewayCommand(userLoginId, orderId, cardType, cardNo, null);
                }
            }

            public record Response(
                Long id,
                String userLoginId,
                Long orderId,
                PaymentCardType cardType,
                Long amount,
                PaymentStatus status,
                PaymentPendingReason pendingReason,
                String transactionKey,
                String reason
            ) {
                public static Response from(PaymentInfo info) {
                    return new Response(
                        info.id(),
                        info.userLoginId(),
                        info.orderId(),
                        info.cardType(),
                        info.amount(),
                        info.status(),
                        info.pendingReason(),
                        info.transactionKey(),
                        info.reason()
                    );
                }
            }
        }
    }

    public static final class Callback {

        private Callback() {}

        public static final class V1 {

            private V1() {}

            public record Request(
                @NotBlank
                String transactionKey,
                @NotBlank
                String orderId,
                @NotNull
                PaymentCardType cardType,
                @NotBlank
                String cardNo,
                @NotNull
                Long amount,
                @NotNull
                PaymentGatewayStatus status,
                String reason
            ) {
                public PaymentCallbackCommand toCommand() {
                    return new PaymentCallbackCommand(
                        transactionKey,
                        PaymentOrderIdMapper.toOrderId(orderId),
                        cardType,
                        cardNo,
                        amount,
                        status,
                        reason
                    );
                }
            }
        }
    }
}
