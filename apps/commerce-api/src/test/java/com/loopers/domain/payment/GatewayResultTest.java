package com.loopers.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayResultTest {

    @DisplayName("ACCEPTED인데 transactionKey가 없으면 생성 시 예외가 발생한다")
    @Test
    void throws_whenAcceptedWithoutKey() {
        assertThatThrownBy(() -> new GatewayResult(GatewayResult.Outcome.ACCEPTED, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("ACCEPTED인데 transactionKey가 비어 있거나 공백이면 생성 시 예외가 발생한다")
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void throws_whenAcceptedWithBlankKey(String blank) {
        assertThatThrownBy(() -> GatewayResult.accepted(blank))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("미접수(PENDING/REJECTED)인데 transactionKey가 있으면 생성 시 예외가 발생한다")
    @Test
    void throws_whenNotAcceptedWithKey() {
        assertThatThrownBy(() -> new GatewayResult(GatewayResult.Outcome.PENDING, "tx-1"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("accepted 팩토리는 거래키를 담은 접수 결과를 만든다")
    @Test
    void accepted_ok() {
        GatewayResult result = GatewayResult.accepted("tx-1");

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.transactionKey()).isEqualTo("tx-1");
    }

    @DisplayName("pending 팩토리는 거래키 없는 '접수 불명' 결과를 만든다")
    @Test
    void pending_ok() {
        GatewayResult result = GatewayResult.pending();

        assertThat(result.isAccepted()).isFalse();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.transactionKey()).isNull();
    }

    @DisplayName("rejected 팩토리는 거래키 없는 '미접수 확정(서킷 OPEN)' 결과를 만든다")
    @Test
    void rejected_ok() {
        GatewayResult result = GatewayResult.rejected();

        assertThat(result.isRejected()).isTrue();
        assertThat(result.isAccepted()).isFalse();
        assertThat(result.transactionKey()).isNull();
    }
}
