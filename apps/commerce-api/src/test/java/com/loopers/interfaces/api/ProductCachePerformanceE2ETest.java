package com.loopers.interfaces.api;

import com.loopers.support.dataloader.ProductDataLoader;
import com.loopers.utils.DatabaseCleanUp;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductCachePerformanceE2ETest {

    private static final int K6_TIMEOUT_SECONDS = 120;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeAll
    void loadData() throws Exception {
        new ProductDataLoader(jdbcTemplate).run(null);
        log.info("데이터 로드 완료. 서버 포트: {}", port);
    }

    @AfterAll
    void cleanUp() {
        Set<String> keys = redisTemplate.keys("product:cache:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("캐시 미스 → 캐시 히트 순차 성능 비교 (k6)")
    @Test
    void cachePerformanceComparison() throws Exception {
        String baseUrl = "http://localhost:" + port;
        // 테스트 JVM 워킹 디렉토리 = apps/commerce-api/ → 루트 2단계 위
        Path projectRoot = Paths.get("..","..").toAbsolutePath().normalize();
        Path k6Script = projectRoot.resolve(Paths.get("k6", "product-cache.js"));

        log.info("k6 실행 시작 — BASE_URL={}, script={}", baseUrl, k6Script);

        ProcessBuilder pb = new ProcessBuilder(
            "k6", "run",
            "--env", "BASE_URL=" + baseUrl,
            k6Script.toString()
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[k6] {}", line);
            }
        }

        boolean finished = process.waitFor(K6_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.error("k6 타임아웃 ({}초 초과)", K6_TIMEOUT_SECONDS);
        }

        int exitCode = finished ? process.exitValue() : -1;
        log.info("k6 종료 코드: {}", exitCode);

        assertThat(exitCode).as("k6 임계값 통과 여부 (exit code 0)").isZero();
    }
}
