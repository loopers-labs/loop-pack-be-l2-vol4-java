package com.loopers.infrastructure.ordering.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ordering.queue.OrderQueueRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(classes = {
    RedisConfig.class,
    RedisCleanUp.class,
    RedisOrderQueueRepository.class
})
@TestPropertySource(properties = {
    "datasource.redis.database=0",
    "datasource.redis.master.host=localhost",
    "datasource.redis.master.port=6379",
    "datasource.redis.replicas[0].host=localhost",
    "datasource.redis.replicas[0].port=6380"
})
class RedisOrderQueueRepositoryTest {

    private final OrderQueueRepository orderQueueRepository;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    RedisOrderQueueRepositoryTest(
        OrderQueueRepository orderQueueRepository,
        RedisCleanUp redisCleanUp
    ) {
        this.orderQueueRepository = orderQueueRepository;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("동시에 대기열에 진입해도 Redis INCR score 기준으로 진입 순서와 대기 인원을 조회한다.")
    @Test
    void preservesEnteredOrder_whenUsersEnterConcurrently() throws Exception {
        // arrange
        int userCount = 10;
        List<String> userIds = List.of(
            "user01",
            "user02",
            "user03",
            "user04",
            "user05",
            "user06",
            "user07",
            "user08",
            "user09",
            "user10"
        );

        // act
        List<OrderQueueRepository.Entry> entries = runConcurrently(userCount, index ->
            orderQueueRepository.enter(userIds.get(index))
        );

        // assert
        List<OrderQueueRepository.Entry> orderedEntries = entries.stream()
            .sorted((first, second) -> Long.compare(first.sequence(), second.sequence()))
            .toList();

        assertAll(
            () -> assertThat(orderedEntries).extracting(OrderQueueRepository.Entry::userId)
                .containsExactlyInAnyOrderElementsOf(userIds),
            () -> assertThat(orderQueueRepository.waitingCount()).isEqualTo(userCount),
            () -> assertThat(orderedEntries)
                .allSatisfy(entry -> assertThat(orderQueueRepository.rank(entry.userId()))
                    .contains((long) orderedEntries.indexOf(entry) + 1))
        );
    }

    @DisplayName("같은 사용자가 다시 대기열에 진입해도 중복 등록하지 않고 기존 순번을 유지한다.")
    @Test
    void keepsOriginalRank_whenSameUserEntersAgain() {
        // arrange
        orderQueueRepository.enter("user1");
        orderQueueRepository.enter("user2");

        // act
        OrderQueueRepository.Entry duplicatedEntry = orderQueueRepository.enter("user1");

        // assert
        assertAll(
            () -> assertThat(duplicatedEntry.userId()).isEqualTo("user1"),
            () -> assertThat(duplicatedEntry.rank()).isEqualTo(1L),
            () -> assertThat(orderQueueRepository.waitingCount()).isEqualTo(2L),
            () -> assertThat(orderQueueRepository.rank("user1")).contains(1L),
            () -> assertThat(orderQueueRepository.rank("user2")).contains(2L)
        );
    }

    @DisplayName("입장 토큰을 TTL과 함께 발급하고 조회, 검증, 삭제한다.")
    @Test
    void managesAdmissionTokenWithTtl() {
        // arrange
        Duration ttl = Duration.ofMillis(200);

        // act
        String token = orderQueueRepository.issueToken("user1", ttl);

        // assert
        assertAll(
            () -> assertThat(token).isNotBlank(),
            () -> assertThat(orderQueueRepository.findToken("user1")).contains(token),
            () -> assertThat(orderQueueRepository.isValidToken("user1", token)).isTrue(),
            () -> assertThat(orderQueueRepository.isValidToken("user1", "invalid-token")).isFalse()
        );

        await().atMost(Duration.ofSeconds(3))
            .untilAsserted(() -> assertThat(orderQueueRepository.findToken("user1")).isEmpty());

        String secondToken = orderQueueRepository.issueToken("user1", Duration.ofMinutes(5));
        orderQueueRepository.deleteToken("user1");

        assertAll(
            () -> assertThat(secondToken).isNotBlank(),
            () -> assertThat(orderQueueRepository.findToken("user1")).isEmpty(),
            () -> assertThat(orderQueueRepository.isValidToken("user1", secondToken)).isFalse()
        );
    }

    @DisplayName("대기열 앞에서 N명을 꺼내 입장 토큰을 발급하고 남은 사용자의 순번을 앞으로 당긴다.")
    @Test
    void admitsNextUsersAndIssuesTokens() {
        // arrange
        orderQueueRepository.enter("user1");
        orderQueueRepository.enter("user2");
        orderQueueRepository.enter("user3");

        // act
        List<OrderQueueRepository.Admitted> admitted = orderQueueRepository.admitNext(2, Duration.ofMinutes(5));

        // assert
        assertAll(
            () -> assertThat(admitted).extracting(OrderQueueRepository.Admitted::userId)
                .containsExactly("user1", "user2"),
            () -> assertThat(admitted).extracting(OrderQueueRepository.Admitted::token)
                .allSatisfy(token -> assertThat(token).isNotBlank()),
            () -> assertThat(orderQueueRepository.rank("user1")).isEmpty(),
            () -> assertThat(orderQueueRepository.rank("user2")).isEmpty(),
            () -> assertThat(orderQueueRepository.rank("user3")).contains(1L),
            () -> assertThat(orderQueueRepository.waitingCount()).isEqualTo(1L),
            () -> assertThat(orderQueueRepository.findToken("user1")).contains(admitted.get(0).token()),
            () -> assertThat(orderQueueRepository.findToken("user2")).contains(admitted.get(1).token())
        );
    }

    private <T> List<T> runConcurrently(int requestCount, IndexedTask<T> task) throws Exception {
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        List<Future<T>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                int index = i;
                futures.add(executorService.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return task.call(index);
                }));
            }
            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executorService.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface IndexedTask<T> {
        T call(int index);
    }
}
