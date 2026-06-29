package com.loopers.interfaces.api.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.payment.PaymentFacade;
import com.loopers.domain.payment.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentV1Controller.class)
class PaymentV1ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentFacade paymentFacade;

    @Test
    @DisplayName("결제 요청(payments) 시 HTTP 200과 paymentId가 반환된다.")
    void processPayment_ApiSuccess() throws Exception {
        // given
        PaymentV1Dto.PaymentRequest request = new PaymentV1Dto.PaymentRequest(
                100L,
                PaymentMethod.CARD,
                new BigDecimal("50000")
        );

        given(paymentFacade.processPayment(eq(100L), eq(PaymentMethod.CARD), any(BigDecimal.class)))
                .willReturn(500L);

        // when & then
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.paymentId").value(500));
    }

    @Test
    @DisplayName("서킷 브레이커 OPEN 상태 시 결제 요청을 하면 503 SERVICE_UNAVAILABLE을 반환한다.")
    void processPayment_CircuitBreakerOpen_Returns503() throws Exception {
        // given
        PaymentV1Dto.PaymentRequest request = new PaymentV1Dto.PaymentRequest(
                100L,
                PaymentMethod.CARD,
                new BigDecimal("50000")
        );

        io.github.resilience4j.circuitbreaker.CircuitBreaker realCircuitBreaker = io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("pgCircuitBreaker");

        io.github.resilience4j.circuitbreaker.CallNotPermittedException exception =
                io.github.resilience4j.circuitbreaker.CallNotPermittedException.createCallNotPermittedException(realCircuitBreaker);

        given(paymentFacade.processPayment(eq(100L), eq(PaymentMethod.CARD), any(BigDecimal.class)))
                .willThrow(exception);

        // when & then
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.meta.result").value("FAIL"))
                .andExpect(jsonPath("$.meta.errorCode").value("PAYMENT-503"))
                .andExpect(jsonPath("$.meta.message").value("현재 외부 결제 시스템 장애로 결제가 일시 중단되었습니다."));
    }
}
