package com.loopers.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EntityId — XXX_ULID 형식의 엔티티 ID를 생성한다.")
class EntityIdTest {

    @DisplayName("생성된 ID는 {code}_{26자 ULID} 형식을 따른다.")
    @Test
    void generate_followsPrefixUlidFormat() {
        // Act
        String id = EntityId.generate("PAY");

        // Assert
        assertThat(id).matches("PAY_[0-9A-HJKMNP-TV-Z]{26}"); // Crockford Base32, 26자
    }

    @DisplayName("동일 코드로 연속 생성해도 매번 다른 ID가 나온다.")
    @Test
    void generate_producesUniqueIds() {
        // Arrange
        Set<String> ids = new HashSet<>();

        // Act
        for (int i = 0; i < 100_000; i++) {
            ids.add(EntityId.generate("USR"));
        }

        // Assert
        assertThat(ids).hasSize(100_000);
    }

    @DisplayName("monotonic 생성이라 같은 ms 내에서도 사전식으로 정렬 증가한다.")
    @Test
    void generate_isMonotonicallyOrdered() {
        // Act
        String first = EntityId.generate("ORD");
        String second = EntityId.generate("ORD");

        // Assert — prefix가 같으므로 ULID 부분이 정렬 증가
        assertThat(second).isGreaterThan(first);
    }

    @DisplayName("멀티 스레드에서 동시에 생성해도 충돌 없이 모두 유일하다.")
    @Test
    void generate_isThreadSafe() throws InterruptedException {
        // Arrange
        int threads = 16;
        int perThread = 10_000;
        Set<String> ids = ConcurrentHashMap.newKeySet();
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        // Act
        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                for (int i = 0; i < perThread; i++) {
                    ids.add(EntityId.generate("LIK"));
                }
            });
        }
        executor.shutdown();
        boolean finished = executor.awaitTermination(30, TimeUnit.SECONDS);

        // Assert
        assertThat(finished).isTrue();
        assertThat(ids).hasSize(threads * perThread);
    }
}
