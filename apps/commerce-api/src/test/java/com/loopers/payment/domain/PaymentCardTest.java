package com.loopers.payment.domain;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class PaymentCardTest {

    @DisplayName("카드 타입과 카드 번호가 주어지면, 결제 카드 정보를 마스킹해서 생성한다.")
    @Test
    void createsMaskedPaymentCard_whenCardTypeAndCardNoAreProvided() {
        // arrange
        CardType cardType = CardType.SAMSUNG;
        String cardNo = "1234-5678-9814-1451";

        // act
        PaymentCard paymentCard = PaymentCard.of(cardType, cardNo);

        // assert
        assertAll(
            () -> assertThat(paymentCard.getType()).isEqualTo(cardType),
            () -> assertThat(paymentCard.getMaskedNo()).isEqualTo("1234-****-****-1451")
        );
    }

    @DisplayName("카드 번호가 숫자 4자리 그룹 형식이 아니면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenCardNoFormatIsInvalid() {
        // arrange
        String cardNo = "abcd-5678-9814-1451";

        // act & assert
        assertThatThrownBy(() -> PaymentCard.of(CardType.SAMSUNG, cardNo))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }
}
