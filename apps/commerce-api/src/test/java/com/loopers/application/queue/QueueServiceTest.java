package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.QueueDomainService;
import com.loopers.domain.queue.QueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueServiceTest {

    private QueueService queueService;
    private FakeQueueRepository fakeQueueRepository;
    private FakeEntryTokenRepository fakeEntryTokenRepository;

    @BeforeEach
    void setUp() {
        fakeQueueRepository = new FakeQueueRepository();
        fakeEntryTokenRepository = new FakeEntryTokenRepository();
        queueService = new QueueService(fakeQueueRepository, fakeEntryTokenRepository, new QueueDomainService());
    }

    @DisplayName("대기열에 진입할 때,")
    @Nested
    class Enter {

        @DisplayName("처음 진입하면, 순번 0을 반환한다.")
        @Test
        void returnsPositionZero_whenFirstUserEnters() {
            // act
            QueueInfo info = queueService.enter(1L);

            // assert
            assertThat(info.position()).isEqualTo(0L);
            assertThat(info.token()).isNull();
        }

        @DisplayName("이미 대기 중인 유저가 다시 진입해도, 기존 순번을 그대로 반환한다.")
        @Test
        void returnsSamePosition_whenAlreadyEntered() {
            // arrange
            queueService.enter(1L);
            queueService.enter(2L);

            // act
            QueueInfo again = queueService.enter(1L);

            // assert
            assertThat(again.position()).isEqualTo(0L);
        }
    }

    @DisplayName("순번을 조회할 때,")
    @Nested
    class Position {

        @DisplayName("대기열에 진입한 기록이 없으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNeverEntered() {
            // act & assert
            assertThatThrownBy(() -> queueService.position(1L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("토큰이 아직 발급되지 않았으면, 순번과 예상 대기시간을 반환한다.")
        @Test
        void returnsPosition_whenTokenNotIssuedYet() {
            // arrange
            queueService.enter(1L);

            // act
            QueueInfo info = queueService.position(1L);

            // assert
            assertThat(info.position()).isEqualTo(0L);
            assertThat(info.token()).isNull();
        }

        @DisplayName("토큰이 이미 발급됐으면, 순번 0과 토큰을 반환한다.")
        @Test
        void returnsToken_whenAlreadyIssued() {
            // arrange
            queueService.enter(1L);
            String token = fakeEntryTokenRepository.issue(1L);

            // act
            QueueInfo info = queueService.position(1L);

            // assert
            assertThat(info.position()).isEqualTo(0L);
            assertThat(info.token()).isEqualTo(token);
        }
    }

    @DisplayName("토큰을 검증할 때,")
    @Nested
    class ValidateToken {

        @DisplayName("발급된 토큰과 일치하면, true를 반환한다.")
        @Test
        void returnsTrue_whenTokenMatches() {
            // arrange
            String token = fakeEntryTokenRepository.issue(1L);

            // act & assert
            assertThat(queueService.validateToken(1L, token)).isTrue();
        }

        @DisplayName("토큰이 없거나 일치하지 않으면, false를 반환한다.")
        @Test
        void returnsFalse_whenTokenMissingOrMismatched() {
            // act & assert
            assertThat(queueService.validateToken(1L, "wrong-token")).isFalse();
        }
    }

    @DisplayName("토큰을 삭제할 때,")
    @Nested
    class DeleteToken {

        @DisplayName("삭제 후 조회하면, 더 이상 존재하지 않는다.")
        @Test
        void removesToken() {
            // arrange
            fakeEntryTokenRepository.issue(1L);

            // act
            queueService.deleteToken(1L);

            // assert
            assertThat(fakeEntryTokenRepository.find(1L)).isEmpty();
        }
    }

    private static class FakeQueueRepository implements QueueRepository {

        private final Map<String, Double> scores = new LinkedHashMap<>();

        @Override
        public long enter(Long userId, long timestampMillis) {
            scores.putIfAbsent(String.valueOf(userId), (double) timestampMillis);
            return rank(userId).orElseThrow();
        }

        @Override
        public Optional<Long> rank(Long userId) {
            List<String> sorted = scores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();
            int index = sorted.indexOf(String.valueOf(userId));
            return index < 0 ? Optional.empty() : Optional.of((long) index);
        }

        @Override
        public long size() {
            return scores.size();
        }

        @Override
        public List<Long> popMin(int count) {
            List<String> sorted = scores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .limit(count)
                .toList();
            sorted.forEach(scores::remove);
            return sorted.stream().map(Long::valueOf).toList();
        }
    }

    private static class FakeEntryTokenRepository implements EntryTokenRepository {

        private final Map<Long, String> tokens = new HashMap<>();

        @Override
        public String issue(Long userId) {
            String token = "fake-token-" + userId;
            tokens.put(userId, token);
            return token;
        }

        @Override
        public Optional<String> find(Long userId) {
            return Optional.ofNullable(tokens.get(userId));
        }

        @Override
        public void delete(Long userId) {
            tokens.remove(userId);
        }
    }
}
