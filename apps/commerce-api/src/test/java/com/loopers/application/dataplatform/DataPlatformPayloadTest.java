package com.loopers.application.dataplatform;

import com.loopers.domain.dataplatform.DataPlatformPayload;
import com.loopers.domain.order.event.OrderPlaced;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataPlatformPayloadTest {

    @DisplayName("OrderPlaced 를 payload 로 변환한다.")
    @Test
    void mapsFromOrderPlaced() {
        OrderPlaced event = new OrderPlaced(
            100L, 7L, 15000L,
            List.of(new OrderPlaced.Line(11L, 2), new OrderPlaced.Line(12L, 1)),
            ZonedDateTime.now());

        DataPlatformPayload payload = DataPlatformPayload.from(event);

        assertThat(payload.orderId()).isEqualTo(100L);
        assertThat(payload.userId()).isEqualTo(7L);
        assertThat(payload.finalAmount()).isEqualTo(15000L);
        assertThat(payload.items()).hasSize(2);
        assertThat(payload.items().get(0).productId()).isEqualTo(11L);
        assertThat(payload.items().get(0).quantity()).isEqualTo(2);
    }
}
