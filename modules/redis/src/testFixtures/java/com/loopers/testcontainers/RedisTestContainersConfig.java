package com.loopers.testcontainers;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.redis.testcontainers.RedisContainer;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.utility.DockerImageName;

@Configuration
public class RedisTestContainersConfig {

    private static final Logger logger = Logger.getLogger(RedisTestContainersConfig.class.getName());
    private static final String FALLBACK_FLAG = "TESTCONTAINERS_FALLBACK_ENABLED";
    private static final String LOCAL_REDIS_MASTER = "localhost:6379";
    private static final String LOCAL_REDIS_REPLICA = "localhost:6380";

    private static RedisContainer redisContainer;

    static {
        try {
            // Redis 이미지를 고정 버전으로 설정 (latest 대신 7-alpine 사용)
            redisContainer = new RedisContainer(DockerImageName.parse("redis:7-alpine"));
            redisContainer.start();
            System.setProperty("datasource.redis.database", "0");
            System.setProperty("datasource.redis.master.host", redisContainer.getHost());
            System.setProperty("datasource.redis.master.port", String.valueOf(redisContainer.getFirstMappedPort()));
            System.setProperty("datasource.redis.replicas[0].host", redisContainer.getHost());
            System.setProperty("datasource.redis.replicas[0].port", String.valueOf(redisContainer.getFirstMappedPort()));
        } catch (Exception e) {
            String fallbackEnabled = System.getenv(FALLBACK_FLAG);

            if ("true".equalsIgnoreCase(fallbackEnabled)) {
                // 명시적 폴백 플래그가 활성화된 경우만 로컬 Redis 사용
                logger.log(Level.WARNING,
                    "Docker/Testcontainers 연결 실패. 로컬 Redis로 폴백합니다. (MASTER: " + LOCAL_REDIS_MASTER + ", REPLICA: " + LOCAL_REDIS_REPLICA + ")", e);
                // redis.yml test profile의 기본값(localhost:6379/6380)이 사용됨
            } else {
                // 기본 동작: fail-fast
                logger.log(Level.SEVERE,
                    "Docker/Testcontainers 시작 실패. 폴백을 사용하려면 " + FALLBACK_FLAG + "=true 환경변수를 설정하세요.", e);
                throw new IllegalStateException("Testcontainers 초기화 실패 - Docker를 사용할 수 없습니다.", e);
            }
        }
    }

    public RedisTestContainersConfig() {
        // 프로퍼티 설정은 static block에서 처리
    }
}
