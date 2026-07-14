package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class QueueFacadeTest {

    @InjectMocks private QueueFacade facade;
    @Mock private WaitingQueueRepository waitingQueueRepository;
    @Mock private EntryTokenRepository entryTokenRepository;

    private static final Long USER_ID = 1L;

    @DisplayName("대기열 진입 시,")
    @Nested
    class Enter {

        @DisplayName("신규 유저는 대기열에 등록되고 WAITING 상태로 순번과 대기 인원을 반환한다.")
        @Test
        void returnsWaitingPosition_whenNewUserEnters() {
            // arrange
            given(entryTokenRepository.find(USER_ID)).willReturn(Optional.empty());
            given(waitingQueueRepository.enter(eq(USER_ID), anyLong())).willReturn(true);
            given(waitingQueueRepository.findRank(USER_ID)).willReturn(Optional.of(4L));
            given(waitingQueueRepository.countWaiting()).willReturn(10L);

            // act
            QueuePositionInfo info = facade.enter(USER_ID);

            // assert
            assertAll(
                () -> assertThat(info.status()).isEqualTo(QueuePositionInfo.QueueStatus.WAITING),
                () -> assertThat(info.position()).isEqualTo(5L),
                () -> assertThat(info.totalWaiting()).isEqualTo(10L),
                () -> assertThat(info.token()).isNull()
            );
        }

        @DisplayName("이미 대기 중인 유저가 재진입해도 예외 없이 기존 순번을 반환한다.")
        @Test
        void returnsExistingPosition_whenAlreadyWaiting() {
            // arrange
            given(entryTokenRepository.find(USER_ID)).willReturn(Optional.empty());
            given(waitingQueueRepository.enter(eq(USER_ID), anyLong())).willReturn(false);
            given(waitingQueueRepository.findRank(USER_ID)).willReturn(Optional.of(0L));
            given(waitingQueueRepository.countWaiting()).willReturn(3L);

            // act
            QueuePositionInfo info = facade.enter(USER_ID);

            // assert
            assertAll(
                () -> assertThat(info.status()).isEqualTo(QueuePositionInfo.QueueStatus.WAITING),
                () -> assertThat(info.position()).isEqualTo(1L),
                () -> assertThat(info.totalWaiting()).isEqualTo(3L)
            );
        }

        @DisplayName("이미 입장 토큰을 발급받은 유저는 대기열에 다시 넣지 않고 READY와 토큰을 반환한다.")
        @Test
        void returnsReadyWithoutReEnqueue_whenTokenAlreadyIssued() {
            // arrange
            given(entryTokenRepository.find(USER_ID)).willReturn(Optional.of("issued-token"));

            // act
            QueuePositionInfo info = facade.enter(USER_ID);

            // assert
            assertAll(
                () -> assertThat(info.status()).isEqualTo(QueuePositionInfo.QueueStatus.READY),
                () -> assertThat(info.token()).isEqualTo("issued-token"),
                () -> assertThat(info.position()).isNull()
            );
            then(waitingQueueRepository).should(never()).enter(anyLong(), anyLong());
        }
    }

    @DisplayName("순번 조회 시,")
    @Nested
    class GetPosition {

        @DisplayName("대기 중인 유저는 WAITING 상태로 1-based 순번, 대기 인원, 예상 대기 시간, 권장 폴링 주기를 반환한다.")
        @Test
        void returnsWaitingInfo_whenWaiting() {
            // arrange
            given(entryTokenRepository.find(USER_ID)).willReturn(Optional.empty());
            given(waitingQueueRepository.findRank(USER_ID)).willReturn(Optional.of(0L));
            given(waitingQueueRepository.countWaiting()).willReturn(1L);

            // act
            QueuePositionInfo info = facade.getPosition(USER_ID);

            // assert — 순번 1은 다음 배치(3초 후) 입장 대상
            assertAll(
                () -> assertThat(info.status()).isEqualTo(QueuePositionInfo.QueueStatus.WAITING),
                () -> assertThat(info.position()).isEqualTo(1L),
                () -> assertThat(info.totalWaiting()).isEqualTo(1L),
                () -> assertThat(info.estimatedWaitSeconds()).isEqualTo(3L),
                () -> assertThat(info.retryAfterSeconds()).isEqualTo(3L),
                () -> assertThat(info.token()).isNull()
            );
        }

        @DisplayName("배치 크기를 넘는 순번은 배치 수만큼 예상 대기 시간이 늘어난다.")
        @Test
        void estimatesLongerWait_whenPositionExceedsBatchSize() {
            // arrange — 순번 31 = 두 번째 배치 → 2 × 3초
            given(entryTokenRepository.find(USER_ID)).willReturn(Optional.empty());
            given(waitingQueueRepository.findRank(USER_ID)).willReturn(Optional.of(30L));
            given(waitingQueueRepository.countWaiting()).willReturn(100L);

            // act
            QueuePositionInfo info = facade.getPosition(USER_ID);

            // assert
            assertAll(
                () -> assertThat(info.position()).isEqualTo(31L),
                () -> assertThat(info.estimatedWaitSeconds()).isEqualTo(6L)
            );
        }

        @DisplayName("토큰이 발급된 유저는 READY 상태로 토큰을 반환한다.")
        @Test
        void returnsReadyWithToken_whenTokenIssued() {
            // arrange
            given(entryTokenRepository.find(USER_ID)).willReturn(Optional.of("issued-token"));

            // act
            QueuePositionInfo info = facade.getPosition(USER_ID);

            // assert
            assertAll(
                () -> assertThat(info.status()).isEqualTo(QueuePositionInfo.QueueStatus.READY),
                () -> assertThat(info.token()).isEqualTo("issued-token"),
                () -> assertThat(info.position()).isNull(),
                () -> assertThat(info.retryAfterSeconds()).isNull()
            );
            then(waitingQueueRepository).should(never()).findRank(anyLong());
        }

        @DisplayName("대기열에 없고 토큰도 없는 유저 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotInQueueAndNoToken() {
            // arrange
            given(entryTokenRepository.find(USER_ID)).willReturn(Optional.empty());
            given(waitingQueueRepository.findRank(USER_ID)).willReturn(Optional.empty());

            // act
            CoreException exception = assertThrows(CoreException.class, () -> facade.getPosition(USER_ID));

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            then(waitingQueueRepository).should(never()).countWaiting();
        }
    }
}
