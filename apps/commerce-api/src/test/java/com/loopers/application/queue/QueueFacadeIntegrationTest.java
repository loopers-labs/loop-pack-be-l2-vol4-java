package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 백그라운드 스케줄러가 3초마다 대기열을 pop하면 순번 검증이 비결정적이 되므로 비활성화
@SpringBootTest(properties = "queue.scheduler.enabled=false")
class QueueFacadeIntegrationTest {

    @Autowired private QueueFacade queueFacade;
    @Autowired private WaitingQueueRepository waitingQueueRepository;
    @Autowired private EntryTokenRepository entryTokenRepository;
    @Autowired private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("대기열 진입 시,")
    @Nested
    class Enter {

        @DisplayName("먼저 진입한 유저가 앞 순번을 받는다.")
        @Test
        void assignsPositionsInEntryOrder() {
            // arrange & act
            QueuePositionInfo first = queueFacade.enter(1L);
            QueuePositionInfo second = queueFacade.enter(2L);
            QueuePositionInfo third = queueFacade.enter(3L);

            // assert
            assertAll(
                () -> assertThat(first.position()).isEqualTo(1L),
                () -> assertThat(second.position()).isEqualTo(2L),
                () -> assertThat(third.position()).isEqualTo(3L),
                () -> assertThat(third.totalWaiting()).isEqualTo(3L)
            );
        }

        @DisplayName("이미 대기 중인 유저가 재진입해도 순번이 뒤로 밀리지 않는다.")
        @Test
        void keepsOriginalPosition_whenReEntering() throws InterruptedException {
            // arrange
            queueFacade.enter(1L);
            Thread.sleep(5); // score(진입 시각)가 확실히 달라지도록
            queueFacade.enter(2L);
            Thread.sleep(5);

            // act — 1번 유저 재진입
            QueuePositionInfo reEntered = queueFacade.enter(1L);

            // assert
            assertAll(
                () -> assertThat(reEntered.position()).isEqualTo(1L),
                () -> assertThat(reEntered.totalWaiting()).isEqualTo(2L)
            );
        }
    }

    @DisplayName("순번 조회 시,")
    @Nested
    class GetPosition {

        @DisplayName("대기 중인 유저는 현재 순번, 전체 대기 인원, 예상 대기 시간, 권장 폴링 주기를 조회할 수 있다.")
        @Test
        void returnsPositionAndTotalWaiting() {
            // arrange
            queueFacade.enter(1L);
            queueFacade.enter(2L);

            // act
            QueuePositionInfo info = queueFacade.getPosition(2L);

            // assert
            assertAll(
                () -> assertThat(info.status()).isEqualTo(QueuePositionInfo.QueueStatus.WAITING),
                () -> assertThat(info.position()).isEqualTo(2L),
                () -> assertThat(info.totalWaiting()).isEqualTo(2L),
                () -> assertThat(info.estimatedWaitSeconds()).isEqualTo(QueuePolicy.estimateWaitSeconds(2)),
                () -> assertThat(info.retryAfterSeconds()).isEqualTo(QueuePolicy.RETRY_AFTER_SECONDS),
                () -> assertThat(info.token()).isNull()
            );
        }

        @DisplayName("대기열에 진입하지 않은 유저 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotInQueue() {
            // arrange — 아무도 진입하지 않음

            // act
            CoreException exception = assertThrows(CoreException.class, () -> queueFacade.getPosition(99L));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("토큰 발급 후 폴링 시,")
    @Nested
    class AfterTokenIssued {

        @DisplayName("스케줄러가 대기열에서 꺼내 토큰을 발급한 유저는 READY 상태와 토큰을 응답받는다.")
        @Test
        void returnsReadyWithToken_afterSchedulerIssuesToken() {
            // arrange — 스케줄러 동작을 재현: 대기열에서 pop 후 토큰 발급
            queueFacade.enter(1L);
            waitingQueueRepository.popMin(1);
            String issuedToken = entryTokenRepository.issue(1L, Duration.ofMinutes(5));

            // act
            QueuePositionInfo info = queueFacade.getPosition(1L);

            // assert
            assertAll(
                () -> assertThat(info.status()).isEqualTo(QueuePositionInfo.QueueStatus.READY),
                () -> assertThat(info.token()).isEqualTo(issuedToken),
                () -> assertThat(info.position()).isNull()
            );
        }

        @DisplayName("토큰을 발급받은 유저가 다시 진입해도 대기열에 등록되지 않고 READY를 응답받는다.")
        @Test
        void doesNotReEnqueue_whenTokenHolderEnters() {
            // arrange
            String issuedToken = entryTokenRepository.issue(1L, Duration.ofMinutes(5));

            // act
            QueuePositionInfo info = queueFacade.enter(1L);

            // assert
            assertAll(
                () -> assertThat(info.status()).isEqualTo(QueuePositionInfo.QueueStatus.READY),
                () -> assertThat(info.token()).isEqualTo(issuedToken),
                () -> assertThat(waitingQueueRepository.countWaiting()).isEqualTo(0L)
            );
        }

        @DisplayName("토큰이 TTL 만료된 유저는 미진입 상태(NOT_FOUND)가 되어 재진입이 필요하다.")
        @Test
        void requiresReEntry_afterTokenExpired() throws InterruptedException {
            // arrange
            entryTokenRepository.issue(1L, Duration.ofMillis(200));
            Thread.sleep(400);

            // act
            CoreException exception = assertThrows(CoreException.class, () -> queueFacade.getPosition(1L));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
