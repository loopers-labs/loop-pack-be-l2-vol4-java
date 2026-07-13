package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueRedisStore;
import com.loopers.domain.queue.QueueRedisStore.EnterResult;
import com.loopers.domain.queue.QueueRedisStore.QueueStatus;
import com.loopers.domain.queue.QueueRedisStore.TokenLockResult;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link QueueRedisStoreImpl} 통합 테스트 — 실제 Redis(Testcontainers)를 사용해
 * 대기열/토큰 상태 전이가 {@link QueueRedisStore}의 문서화된 계약대로 동작하는지 검증한다.
 */
@SpringBootTest
class QueueRedisStoreImplTest {

    @Autowired
    private QueueRedisStore queueRedisStore;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        redisCleanUp.truncateAll();
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("enter()는 최초 호출 시 WAITING을 반환하고, 같은 유저의 반복 호출은 순위를 바꾸지 않는다")
    @Test
    void enterReturnsWaiting_andRepeatedCallIsIdempotentOnRank() {
        // Arrange
        long productId = 1001L;
        long userId = 1L;
        long otherUserId = 2L;

        // Act
        EnterResult first = queueRedisStore.enter(productId, userId, 1_000L);
        EnterResult second = queueRedisStore.enter(productId, otherUserId, 2_000L);
        Long positionBeforeRepeat = queueRedisStore.status(productId, userId).position();

        // 동일 유저가 다시 enter() 호출 — NX이므로 기존 점수(순위)가 유지되어야 한다.
        EnterResult repeated = queueRedisStore.enter(productId, userId, 999_999L);
        Long positionAfterRepeat = queueRedisStore.status(productId, userId).position();

        // Assert
        assertThat(first).isEqualTo(EnterResult.WAITING);
        assertThat(second).isEqualTo(EnterResult.WAITING);
        assertThat(repeated).isEqualTo(EnterResult.WAITING);
        assertThat(positionBeforeRepeat).isEqualTo(1L);
        assertThat(positionAfterRepeat).isEqualTo(positionBeforeRepeat);

        QueueStatus otherStatus = queueRedisStore.status(productId, otherUserId);
        assertThat(otherStatus.position()).isEqualTo(2L);
    }

    @DisplayName("enter()는 이미 토큰을 보유한 유저에 대해 ADMITTED를 반환한다")
    @Test
    void enterReturnsAdmitted_whenTokenAlreadyExists() {
        // Arrange
        long productId = 1002L;
        long userId = 1L;
        queueRedisStore.issueToken(productId, userId, Duration.ofMinutes(5));

        // Act
        EnterResult result = queueRedisStore.enter(productId, userId, 1_000L);

        // Assert
        assertThat(result).isEqualTo(EnterResult.ADMITTED);
    }

    @DisplayName("admitBatch()는 입장 시각 순서(FIFO)대로 대기열 앞에서부터 뽑는다")
    @Test
    void admitBatchAdmitsInFifoOrder() {
        // Arrange
        long productId = 1003L;
        List<Long> userIds = List.of(11L, 12L, 13L, 14L, 15L);
        long now = 1_000L;
        for (Long userId : userIds) {
            queueRedisStore.enter(productId, userId, now);
            now += 10L;
        }

        // Act
        List<Long> admitted = queueRedisStore.admitBatch(productId, 3);

        // Assert
        assertThat(admitted).containsExactly(11L, 12L, 13L);
    }

    @DisplayName("admitBatch()는 대기 인원이 배치 크기보다 적으면 남은 인원만 반환하고, 그 다음 호출은 빈 리스트를 반환한다")
    @Test
    void admitBatchReturnsRemainingOnly_whenFewerThanBatchSizeAreWaiting() {
        // Arrange — 대기자 2명뿐인데 배치 크기는 5
        long productId = 1004L;
        List<Long> userIds = List.of(21L, 22L);
        long now = 1_000L;
        for (Long userId : userIds) {
            queueRedisStore.enter(productId, userId, now);
            now += 10L;
        }

        // Act
        List<Long> firstBatch = queueRedisStore.admitBatch(productId, 5);
        List<Long> secondBatch = queueRedisStore.admitBatch(productId, 5);

        // Assert — 재고 판정은 없으므로, 대기자가 남아있는 한 계속 인원 그대로 뽑히고
        // 다 뽑힌 뒤엔 그냥 빈 리스트일 뿐 특별한 상태(품절 등)로 전이되지 않는다.
        assertThat(firstBatch).containsExactly(21L, 22L);
        assertThat(secondBatch).isEmpty();
    }

    @DisplayName("tryLock/unlock/consumeToken 상태 전이가 문서화된 계약대로 동작한다")
    @Test
    void tokenLockLifecycleTransitionsCorrectly() {
        // Arrange
        long productId = 1005L;
        long userId = 31L;
        long neverIssuedUserId = 32L;
        queueRedisStore.issueToken(productId, userId, Duration.ofMinutes(5));

        // Act & Assert: 최초 tryLock -> OK
        assertThat(queueRedisStore.tryLock(productId, userId)).isEqualTo(TokenLockResult.OK);

        // 잠긴 상태에서 재시도 -> BUSY
        assertThat(queueRedisStore.tryLock(productId, userId)).isEqualTo(TokenLockResult.BUSY);

        // unlock 후 재시도 -> 다시 OK
        queueRedisStore.unlock(productId, userId);
        assertThat(queueRedisStore.tryLock(productId, userId)).isEqualTo(TokenLockResult.OK);

        // consumeToken -> 토큰 삭제, 이후 tryLock -> EXPIRED
        queueRedisStore.consumeToken(productId, userId);
        assertThat(queueRedisStore.tryLock(productId, userId)).isEqualTo(TokenLockResult.EXPIRED);

        // 발급된 적 없는 토큰 -> EXPIRED
        assertThat(queueRedisStore.tryLock(productId, neverIssuedUserId)).isEqualTo(TokenLockResult.EXPIRED);
    }

    @DisplayName("leave()는 아직 대기 중인 유저를 대기열에서 제거한다")
    @Test
    void leaveRemovesStillWaitingUserFromWaitingSet() {
        // Arrange
        long productId = 1006L;
        long leavingUserId = 41L;
        long stayingUserId = 42L;
        queueRedisStore.enter(productId, leavingUserId, 1_000L);
        queueRedisStore.enter(productId, stayingUserId, 2_000L);

        // Act
        queueRedisStore.leave(productId, leavingUserId);

        // Assert: status()에 순위가 없어야 한다
        QueueStatus leftStatus = queueRedisStore.status(productId, leavingUserId);
        assertThat(leftStatus.position()).isNull();
        assertThat(leftStatus.state()).isEqualTo(QueueStatus.State.WAITING);

        // admitBatch로 뽑아도 나간 유저는 포함되지 않는다
        List<Long> admitted = queueRedisStore.admitBatch(productId, 10);
        assertThat(admitted).containsExactly(stayingUserId);
    }
}
