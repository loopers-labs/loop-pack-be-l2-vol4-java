package com.loopers.infrastructure.payment.pg;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * pg-simulator 응답 envelope 역직렬화 단위 테스트.
 *
 * <p>pg-simulator 는 모든 응답을 {@code {"meta": {...}, "data": {...}}} 로 감싼다.
 * Java 클라이언트가 이 envelope 을 벗겨 {@code data} 를 읽는지 검증한다 —
 * envelope 을 벗기지 않으면 transactionKey 가 null 로 들어와 결제 흐름 전체가 무력화된다.
 */
class PgPaymentDtoDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @DisplayName("결제 요청 응답 envelope 에서 data(transactionKey/status) 를 추출한다.")
    @Test
    void unwrapsData_whenTransactionResponseWrappedInEnvelope() throws Exception {
        // arrange — pg-simulator 의 실제 응답 형태
        String json = """
            {
              "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
              "data": { "transactionKey": "20250816:TR:abc123", "status": "PENDING", "reason": null }
            }
            """;

        // act
        PgPaymentDto.PgApiResponse<PgPaymentDto.TransactionResponse> response =
            objectMapper.readValue(json, objectMapper.getTypeFactory()
                .constructParametricType(PgPaymentDto.PgApiResponse.class, PgPaymentDto.TransactionResponse.class));

        // assert
        assertThat(response.data()).isNotNull();
        assertThat(response.data().transactionKey()).isEqualTo("20250816:TR:abc123");
        assertThat(response.data().status()).isEqualTo("PENDING");
    }

    @DisplayName("주문 트랜잭션 목록 응답 envelope 에서 data(transactions) 를 추출한다.")
    @Test
    void unwrapsData_whenOrderTransactionResponseWrappedInEnvelope() throws Exception {
        // arrange
        String json = """
            {
              "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
              "data": {
                "orderId": "1351039135",
                "transactions": [
                  { "transactionKey": "TR:1", "status": "FAILED", "reason": "한도초과" }
                ]
              }
            }
            """;

        // act
        PgPaymentDto.PgApiResponse<PgPaymentDto.OrderTransactionResponse> response =
            objectMapper.readValue(json, objectMapper.getTypeFactory()
                .constructParametricType(PgPaymentDto.PgApiResponse.class, PgPaymentDto.OrderTransactionResponse.class));

        // assert
        assertThat(response.data()).isNotNull();
        assertThat(response.data().transactions()).hasSize(1);
        assertThat(response.data().transactions().get(0).status()).isEqualTo("FAILED");
        assertThat(response.data().transactions().get(0).reason()).isEqualTo("한도초과");
    }
}
