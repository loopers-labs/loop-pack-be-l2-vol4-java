package com.loopers.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayResultTest {

    @DisplayName("accepted=true인데 transactionKey가 없으면 생성 시 예외가 발생한다")
    @Test
    void throws_whenAcceptedWithoutKey() {
        assertThatThrownBy(() -> new GatewayResult(true, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("accepted=false인데 transactionKey가 있으면 생성 시 예외가 발생한다")
    @Test
    void throws_whenPendingWithKey() {
        assertThatThrownBy(() -> new GatewayResult(false, "tx-1"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("accepted 팩토리는 거래키를 담은 접수 결과를 만든다")
    @Test
    void accepted_ok() {
        GatewayResult result = GatewayResult.accepted("tx-1");

        assertThat(result.accepted()).isTrue();
        assertThat(result.transactionKey()).isEqualTo("tx-1");
    }

    @DisplayName("pending 팩토리는 거래키 없는 미접수 결과를 만든다")
    @Test
    void pending_ok() {
        GatewayResult result = GatewayResult.pending();

        assertThat(result.accepted()).isFalse();
        assertThat(result.transactionKey()).isNull();
    }
}
