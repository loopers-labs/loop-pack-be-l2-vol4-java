package com.loopers.interfaces.api;

import com.loopers.application.queue.QueueFacade;
import com.loopers.interfaces.api.queue.QueueV1Controller;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ApiControllerAdvice의 DataAccessException 전용 핸들러(7.2)와 CallNotPermittedException 핸들러(8.1)가
// 503 + SERVICE_UNAVAILABLE로 변환하는지 QueueV1Controller를 통해 확인한다. Redis 자체 장애나 CircuitBreaker Open
// 상태를 재현하는 대신 Facade가 해당 예외를 던지도록 만들어, 실제 컨테이너 없이 웹 계층만 슬라이스로 검증한다.
@WebMvcTest(QueueV1Controller.class)
class ApiControllerAdviceServiceUnavailableTest {

    private static final String LOGIN_ID = "user01";
    private static final String LOGIN_PW = "Password1!";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QueueFacade queueFacade;

    @DisplayName("Redis 연결 예외가 발생하면 503 Service Unavailable과 SERVICE_UNAVAILABLE 에러 코드를 반환한다.")
    @Test
    void returnsServiceUnavailable_whenDataAccessExceptionIsThrown() throws Exception {
        // given
        given(queueFacade.enter(LOGIN_ID, LOGIN_PW)).willThrow(new RedisConnectionFailureException("Redis 연결 실패"));

        // when & then
        mockMvc.perform(post("/api/v1/queue/enter")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", LOGIN_PW))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.meta.errorCode").value(ErrorType.SERVICE_UNAVAILABLE.getCode()));
    }

    @DisplayName("CircuitBreaker가 Open 상태여서 호출이 거부되면 503 Service Unavailable과 SERVICE_UNAVAILABLE 에러 코드를 반환한다.")
    @Test
    void returnsServiceUnavailable_whenCircuitBreakerIsOpen() throws Exception {
        // given
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("redis");
        given(queueFacade.enter(LOGIN_ID, LOGIN_PW))
                .willThrow(CallNotPermittedException.createCallNotPermittedException(circuitBreaker));

        // when & then
        mockMvc.perform(post("/api/v1/queue/enter")
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", LOGIN_PW))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.meta.errorCode").value(ErrorType.SERVICE_UNAVAILABLE.getCode()));
    }
}
