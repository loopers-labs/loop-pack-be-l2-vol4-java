package com.loopers.testcontainers;

import com.redis.testcontainers.RedisContainer;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.utility.DockerImageName;

@Configuration
public class RedisTestContainersConfig {
    private static final RedisContainer redisContainer = new RedisContainer(DockerImageName.parse("redis:latest"));

    // static 블록에서 컨테이너 기동 + 프로퍼티 주입한다. 생성자(빈 인스턴스화 시점)에서 설정하면
    // RedisProperties(@ConfigurationProperties) 바인딩보다 늦어 local 기본값(localhost:6379/6380)이
    // 먼저 적용돼 버린다 — 실제 Redis 연결 테스트(캐시 등)에서 컨테이너가 아닌 localhost로 붙어 실패한다.
    static {
        redisContainer.start();
        System.setProperty("datasource.redis.database", "0");
        System.setProperty("datasource.redis.master.host", redisContainer.getHost());
        System.setProperty("datasource.redis.master.port", String.valueOf(redisContainer.getFirstMappedPort()));
        System.setProperty("datasource.redis.replicas[0].host", redisContainer.getHost());
        System.setProperty("datasource.redis.replicas[0].port", String.valueOf(redisContainer.getFirstMappedPort()));
    }
}
