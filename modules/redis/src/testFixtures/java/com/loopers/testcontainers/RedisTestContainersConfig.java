package com.loopers.testcontainers;

import com.redis.testcontainers.RedisContainer;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.utility.DockerImageName;

@Configuration
public class RedisTestContainersConfig {

    private static RedisContainer redisContainer;

    static {
        try {
            redisContainer = new RedisContainer(DockerImageName.parse("redis:latest"));
            redisContainer.start();
            System.setProperty("datasource.redis.database", "0");
            System.setProperty("datasource.redis.master.host", redisContainer.getHost());
            System.setProperty("datasource.redis.master.port", String.valueOf(redisContainer.getFirstMappedPort()));
            System.setProperty("datasource.redis.replicas[0].host", redisContainer.getHost());
            System.setProperty("datasource.redis.replicas[0].port", String.valueOf(redisContainer.getFirstMappedPort()));
        } catch (Exception e) {
            // Docker를 사용할 수 없는 경우 로컬 Redis로 폴백 (redis.yml test profile 기본값 사용)
            System.out.println("[TestContainers] Docker 연결 실패 - 로컬 Redis(localhost:6379/6380)로 대체합니다.");
            // redis.yml test profile에 localhost:6379(master), localhost:6380(replica)가 이미 설정되어 있음
        }
    }

    public RedisTestContainersConfig() {
        // 프로퍼티 설정은 static block에서 처리
    }
}
