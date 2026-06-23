package com.loopers.interfaces.api.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PaymentV1Controller 슬라이스 단위 테스트 (standalone MockMvc — Spring 컨텍스트/Docker 불필요).
 * HTTP 매핑·요청 JSON 역직렬화(CardType enum 포함)·헤더 인증 위임·응답 래핑을 검증한다.
 */
class PaymentV1ControllerTest {

    private PaymentFacade paymentFacade;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        paymentFacade = mock(PaymentFacade.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentV1Controller(paymentFacade)).build();
    }

    @DisplayName("POST /api/v1/payments — 결제 시작 시, 헤더 인증정보와 본문을 Facade에 위임하고 PENDING 결과를 200으로 반환한다.")
    @Test
    void delegatesToFacade_andReturnsPending() throws Exception {
        PaymentInfo info = new PaymentInfo(10L, 7L, "20260623:TR:abc123", PaymentStatus.PENDING, null);
        when(paymentFacade.pay(eq("buyer"), eq("testPw1234"), eq(7L), eq(CardType.SAMSUNG), eq("1234-5678-9012-3456")))
                .thenReturn(info);

        PaymentV1Dto.PayRequest request = new PaymentV1Dto.PayRequest(7L, CardType.SAMSUNG, "1234-5678-9012-3456");

        mockMvc.perform(post("/api/v1/payments")
                        .header("X-Loopers-LoginId", "buyer")
                        .header("X-Loopers-LoginPw", "testPw1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.orderId").value(7))
                .andExpect(jsonPath("$.data.transactionKey").value("20260623:TR:abc123"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        ArgumentCaptor<CardType> cardTypeCaptor = ArgumentCaptor.forClass(CardType.class);
        verify(paymentFacade).pay(eq("buyer"), eq("testPw1234"), eq(7L), cardTypeCaptor.capture(), any());
        assertThat(cardTypeCaptor.getValue()).isEqualTo(CardType.SAMSUNG);
    }
}
