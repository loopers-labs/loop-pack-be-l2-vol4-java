package com.loopers.interfaces.api;

import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.interfaces.api.queue.QueueV1Dto;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 게이트 off(평시, 기본 구성) E2E — 대기열 미운영 중에는 enter/position 자체를 400 으로 거부한다.
 * off 상태에서 진입을 허용하면 입장 스케줄러가 없어 영원히 대기하는 함정이 되기 때문(스펙 API 계약).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueV1ApiGateOffE2ETest {

    private static final String ENTER_ENDPOINT = "/api/v1/queue/enter";
    private static final String POSITION_ENDPOINT = "/api/v1/queue/position";

    private final TestRestTemplate testRestTemplate;
    private final WaitingQueueRepository waitingQueueRepository;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    public QueueV1ApiGateOffE2ETest(
        TestRestTemplate testRestTemplate,
        WaitingQueueRepository waitingQueueRepository,
        RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.waitingQueueRepository = waitingQueueRepository;
        this.redisCleanUp = redisCleanUp;
    }

    @BeforeEach
    void setUp() {
        // 자기완결 클린업 — countWaiting 단언이 선행 테스트의 전역 상태에 좌우되지 않게 한다
        redisCleanUp.truncateAll();
    }

    private HttpHeaders authHeaders(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.HEADER_LOGIN_ID, loginId);
        headers.set(AuthHeaders.HEADER_LOGIN_PW, "Password1!");
        return headers;
    }

    @DisplayName("게이트 off 면 enter 가 400 으로 거부되고, 대기열에 아무도 서지 않는다.")
    @Test
    void rejectsEnter_whenGateOff() {
        // act
        ParameterizedTypeReference<ApiResponse<QueueV1Dto.QueueResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<QueueV1Dto.QueueResponse>> response = testRestTemplate.exchange(
            ENTER_ENDPOINT, HttpMethod.POST, new HttpEntity<>(authHeaders("tester01")), responseType
        );

        // assert
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
            () -> assertThat(waitingQueueRepository.countWaiting()).isZero()
        );
    }

    @DisplayName("게이트 off 면 position 도 400 으로 거부된다.")
    @Test
    void rejectsPosition_whenGateOff() {
        // act
        ParameterizedTypeReference<ApiResponse<QueueV1Dto.QueueResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<QueueV1Dto.QueueResponse>> response = testRestTemplate.exchange(
            POSITION_ENDPOINT, HttpMethod.GET, new HttpEntity<>(authHeaders("tester01")), responseType
        );

        // assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
