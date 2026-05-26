package com.loopers.testcontainers;

import com.redis.testcontainers.RedisContainer;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.utility.DockerImageName;

/**
 * 환경변수 USE_LOCAL_REDIS=true 인 경우, testcontainers 없이 로컬 docker-compose로 띄운 Redis를 사용한다.
 * (Docker 29.x 등 testcontainers 호환 문제 회피용)
 */
@Configuration
public class RedisTestContainersConfig {

    private static final boolean USE_LOCAL_REDIS = "true".equalsIgnoreCase(System.getenv("USE_LOCAL_REDIS"));

    private static final RedisContainer redisContainer;

    static {
        if (USE_LOCAL_REDIS) {
            redisContainer = null;
            System.setProperty("datasource.redis.database", "0");
            System.setProperty("datasource.redis.master.host", "localhost");
            System.setProperty("datasource.redis.master.port", "6379");
            System.setProperty("datasource.redis.replicas[0].host", "localhost");
            System.setProperty("datasource.redis.replicas[0].port", "6380");
        } else {
            redisContainer = new RedisContainer(DockerImageName.parse("redis:latest"));
            redisContainer.start();
        }
    }

    public RedisTestContainersConfig() {
        if (!USE_LOCAL_REDIS) {
            System.setProperty("datasource.redis.database", "0");
            System.setProperty("datasource.redis.master.host", redisContainer.getHost());
            System.setProperty("datasource.redis.master.port", String.valueOf(redisContainer.getFirstMappedPort()));
            System.setProperty("datasource.redis.replicas[0].host", redisContainer.getHost());
            System.setProperty("datasource.redis.replicas[0].port", String.valueOf(redisContainer.getFirstMappedPort()));
        }
    }
}
