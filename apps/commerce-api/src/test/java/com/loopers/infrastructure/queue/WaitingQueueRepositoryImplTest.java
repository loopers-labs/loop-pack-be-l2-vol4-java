package com.loopers.infrastructure.queue;

import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class WaitingQueueRepositoryImplTest {

    @Autowired WaitingQueueRepositoryImpl repository;
    @Autowired RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        redisCleanUp.truncateAll(); // 선행 테스트 잔재에 기대지 않는 자기완결 클린업
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("신규 유저를 enqueue 하면 true 를 반환하고 rank 0 에 선다.")
    @Test
    void enqueueNewUser_returnsTrue() {
        // act
        boolean added = repository.enqueue("userA", 1_000L);

        // assert
        assertAll(
            () -> assertThat(added).isTrue(),
            () -> assertThat(repository.findRank("userA")).contains(0L),
            () -> assertThat(repository.countWaiting()).isEqualTo(1)
        );
    }

    @DisplayName("이미 대기 중인 유저가 더 늦은 시각으로 재진입해도(NX), score 가 유지되어 줄 뒤로 밀리지 않는다.")
    @Test
    void reenter_keepsOriginalRank() {
        // arrange
        repository.enqueue("userA", 1_000L);
        repository.enqueue("userB", 2_000L);

        // act — userA 가 훨씬 늦은 시각으로 재진입 시도
        boolean readded = repository.enqueue("userA", 9_000L);

        // assert — NX 라 score 갱신 없이 기존 순번 유지
        assertAll(
            () -> assertThat(readded).isFalse(),
            () -> assertThat(repository.findRank("userA")).contains(0L),
            () -> assertThat(repository.findRank("userB")).contains(1L),
            () -> assertThat(repository.countWaiting()).isEqualTo(2)
        );
    }

    @DisplayName("대기 중이 아닌 유저의 rank 는 empty 다.")
    @Test
    void findRank_returnsEmpty_whenNotWaiting() {
        // act & assert
        assertThat(repository.findRank("ghost")).isEmpty();
    }

    @DisplayName("peekNextBatch 는 진입 시각 순서대로 조회하되, 대기열에서 제거하지 않는다.")
    @Test
    void peekNextBatch_returnsInScoreOrder_withoutRemoving() {
        // arrange
        repository.enqueue("userC", 3_000L);
        repository.enqueue("userA", 1_000L);
        repository.enqueue("userB", 2_000L);

        // act
        List<String> peeked = repository.peekNextBatch(2);

        // assert — 조회만 하고 순번·인원은 그대로
        assertAll(
            () -> assertThat(peeked).containsExactly("userA", "userB"),
            () -> assertThat(repository.countWaiting()).isEqualTo(3),
            () -> assertThat(repository.findRank("userA")).contains(0L),
            () -> assertThat(repository.findRank("userC")).contains(2L)
        );
    }

    @DisplayName("대기 인원이 배치 크기보다 적으면 있는 만큼만 조회한다.")
    @Test
    void peekNextBatch_returnsAll_whenLessThanCount() {
        // arrange
        repository.enqueue("userA", 1_000L);

        // act & assert
        assertThat(repository.peekNextBatch(10)).containsExactly("userA");
    }

    @DisplayName("빈 대기열에서 peekNextBatch 하면 빈 리스트를 반환한다.")
    @Test
    void peekNextBatch_returnsEmpty_whenQueueEmpty() {
        // act & assert
        assertThat(repository.peekNextBatch(10)).isEmpty();
    }

    @DisplayName("remove 하면 지정한 유저만 제거되고, 남은 유저의 순번이 앞으로 당겨진다.")
    @Test
    void remove_removesOnlySpecified_andShiftsRemaining() {
        // arrange
        repository.enqueue("userA", 1_000L);
        repository.enqueue("userB", 2_000L);
        repository.enqueue("userC", 3_000L);

        // act
        repository.remove(List.of("userA", "userB"));

        // assert
        assertAll(
            () -> assertThat(repository.countWaiting()).isEqualTo(1),
            () -> assertThat(repository.findRank("userA")).isEmpty(),
            () -> assertThat(repository.findRank("userC")).contains(0L)
        );
    }

    @DisplayName("빈 목록으로 remove 해도 대기열은 변하지 않는다.")
    @Test
    void remove_withEmptyList_isNoOp() {
        // arrange
        repository.enqueue("userA", 1_000L);

        // act
        repository.remove(List.of());

        // assert
        assertThat(repository.countWaiting()).isEqualTo(1);
    }
}
