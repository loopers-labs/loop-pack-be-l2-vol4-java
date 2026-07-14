package com.loopers.interfaces.api.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.user.UserModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDate;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class QueueTokenInterceptorTest {

    private QueueTokenInterceptor interceptor;

    @Mock private EntryTokenRepository entryTokenRepository;

    /**
     * 서킷이 닫힌 상태를 재현하는 테스트 더블 — supplier를 실행하고, 예외가 나면 fallback을 호출한다.
     * 실제 Resilience4JCircuitBreakerFactory를 구성하지 않고도 인터셉터의 supplier/fallback 로직을 검증한다.
     */
    private static final CircuitBreaker PASS_THROUGH_BREAKER = new CircuitBreaker() {
        @Override
        public <T> T run(Supplier<T> toRun, Function<Throwable, T> fallback) {
            try {
                return toRun.get();
            } catch (Throwable t) {
                return fallback.apply(t);
            }
        }
    };

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private UserModel currentUser;

    @BeforeEach
    void setUp() {
        interceptor = new QueueTokenInterceptor(entryTokenRepository, PASS_THROUGH_BREAKER);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        currentUser = new UserModel("user123", "encoded!", "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
    }

    @DisplayName("preHandle()을 호출할 때,")
    @Nested
    class PreHandle {

        @DisplayName("GET 요청은 토큰 검증 없이 통과한다.")
        @Test
        void passes_whenNotPostRequest() {
            // arrange
            request.setMethod("GET");

            // act
            boolean result = interceptor.preHandle(request, response, new Object());

            // assert
            assertTrue(result);
            then(entryTokenRepository).should(never()).isValid(anyLong(), anyString());
        }

        @DisplayName("POST 요청에 토큰 헤더가 없으면 UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenTokenHeaderMissing() {
            // arrange
            request.setMethod("POST");
            request.setAttribute("currentUser", currentUser);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                interceptor.preHandle(request, response, new Object())
            );

            // assert — Redis를 조회하기도 전에 401
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
            then(entryTokenRepository).should(never()).isValid(anyLong(), anyString());
        }

        @DisplayName("POST 요청에 유효하지 않은 토큰이면 UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenTokenInvalid() {
            // arrange
            request.setMethod("POST");
            request.setAttribute("currentUser", currentUser);
            request.addHeader("X-Queue-Token", "invalid-token");
            given(entryTokenRepository.isValid(currentUser.getId(), "invalid-token")).willReturn(false);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                interceptor.preHandle(request, response, new Object())
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("POST 요청에 유효한 토큰이면 통과한다.")
        @Test
        void passes_whenTokenValid() {
            // arrange
            request.setMethod("POST");
            request.setAttribute("currentUser", currentUser);
            request.addHeader("X-Queue-Token", "valid-token");
            given(entryTokenRepository.isValid(currentUser.getId(), "valid-token")).willReturn(true);

            // act
            boolean result = interceptor.preHandle(request, response, new Object());

            // assert
            assertTrue(result);
        }

        @DisplayName("Redis 장애로 토큰을 검증할 수 없으면 주문을 막고 503 + Retry-After를 반환한다(fail-closed).")
        @Test
        void throwsServiceUnavailable_whenRedisDown() {
            // arrange
            request.setMethod("POST");
            request.setAttribute("currentUser", currentUser);
            request.addHeader("X-Queue-Token", "any-token");
            willThrow(new RedisConnectionFailureException("Unable to connect to Redis"))
                .given(entryTokenRepository).isValid(currentUser.getId(), "any-token");

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                interceptor.preHandle(request, response, new Object())
            );

            // assert — 401(토큰 없음)이 아니라 503(대기열 장애)으로 구분되고 Retry-After가 실린다
            assertThat(result.getErrorType()).isEqualTo(ErrorType.SERVICE_UNAVAILABLE);
            assertThat(response.getHeader("Retry-After")).isEqualTo("5");
        }
    }

    @DisplayName("afterCompletion()을 호출할 때,")
    @Nested
    class AfterCompletion {

        @DisplayName("POST 요청이 성공(2xx)하면 토큰을 삭제한다.")
        @Test
        void deletesToken_whenPostSucceeds() {
            // arrange
            request.setMethod("POST");
            request.setAttribute("currentUser", currentUser);
            response.setStatus(200);

            // act
            interceptor.afterCompletion(request, response, new Object(), null);

            // assert
            then(entryTokenRepository).should().delete(currentUser.getId());
        }

        @DisplayName("POST 요청이 실패(4xx/5xx)하면 토큰을 삭제하지 않는다.")
        @Test
        void keepsToken_whenPostFails() {
            // arrange
            request.setMethod("POST");
            request.setAttribute("currentUser", currentUser);
            response.setStatus(500);

            // act
            interceptor.afterCompletion(request, response, new Object(), null);

            // assert
            then(entryTokenRepository).should(never()).delete(anyLong());
        }

        @DisplayName("GET 요청은 토큰 삭제 대상이 아니다.")
        @Test
        void doesNothing_whenNotPostRequest() {
            // arrange
            request.setMethod("GET");
            response.setStatus(200);

            // act
            interceptor.afterCompletion(request, response, new Object(), null);

            // assert
            then(entryTokenRepository).should(never()).delete(anyLong());
        }
    }
}
