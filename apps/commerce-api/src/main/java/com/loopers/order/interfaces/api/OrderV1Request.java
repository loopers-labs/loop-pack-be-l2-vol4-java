package com.loopers.order.interfaces.api;

import com.loopers.order.application.OrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public class OrderV1Request {

    public record Create(
        @NotEmpty(message = "주문 항목은 하나 이상이어야 합니다.")
        @Valid
        List<Line> items,

        @NotBlank(message = "수령인명은 필수입니다.")
        @Size(max = 50, message = "수령인명은 50자 이내여야 합니다.")
        String recipientName,

        @NotBlank(message = "연락처는 필수입니다.")
        @Size(max = 20, message = "연락처는 20자 이내여야 합니다.")
        String recipientPhone,

        @NotBlank(message = "우편번호는 필수입니다.")
        @Size(max = 10, message = "우편번호는 10자 이내여야 합니다.")
        String zipcode,

        @NotBlank(message = "기본주소는 필수입니다.")
        @Size(max = 255, message = "기본주소는 255자 이내여야 합니다.")
        String address1,

        @Size(max = 255, message = "상세주소는 255자 이내여야 합니다.")
        String address2,

        Long userCouponId
    ) {
        public OrderCommand.Create toCommand(Long userId) {
            return new OrderCommand.Create(
                userId,
                items.stream().map(Line::toCommandLine).toList(),
                recipientName, recipientPhone, zipcode, address1, address2,
                userCouponId
            );
        }

        public record Line(
            @NotNull(message = "productId 는 필수입니다.")
            Long productId,

            @Positive(message = "주문 수량은 1 이상이어야 합니다.")
            int quantity
        ) {
            OrderCommand.Line toCommandLine() {
                return new OrderCommand.Line(productId, quantity);
            }
        }
    }
}
