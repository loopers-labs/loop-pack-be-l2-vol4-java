package com.loopers.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayLookupTest {

    @DisplayName("FOUND인데 transactionKey가 없으면 생성 시 예외가 발생한다")
    @Test
    void throws_whenFoundWithoutKey() {
        assertThatThrownBy(() -> new GatewayLookup(GatewayLookup.Result.FOUND, null, "SUCCESS", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("FOUND인데 status가 없으면 생성 시 예외가 발생한다")
    @Test
    void throws_whenFoundWithoutStatus() {
        assertThatThrownBy(() -> new GatewayLookup(GatewayLookup.Result.FOUND, "tx-1", null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("NOT_FOUND/UNREACHABLE인데 payload를 담으면 생성 시 예외가 발생한다")
    @Test
    void throws_whenNonFoundCarriesPayload() {
        assertThatThrownBy(() -> new GatewayLookup(GatewayLookup.Result.NOT_FOUND, "tx-1", null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GatewayLookup(GatewayLookup.Result.UNREACHABLE, null, "SUCCESS", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("found/notFound/unreachable 팩토리는 불변식을 만족하는 결과를 만든다")
    @Test
    void factories_ok() {
        assertThat(GatewayLookup.found("tx-1", "SUCCESS", "ok").result()).isEqualTo(GatewayLookup.Result.FOUND);
        assertThat(GatewayLookup.notFound().transactionKey()).isNull();
        assertThat(GatewayLookup.unreachable().status()).isNull();
    }
}
