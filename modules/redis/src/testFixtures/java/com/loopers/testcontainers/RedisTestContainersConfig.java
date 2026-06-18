package com.loopers.testcontainers;

import com.redis.testcontainers.RedisContainer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

public class RedisTestContainersConfig implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final RedisContainer redisContainer = new RedisContainer(DockerImageName.parse("redis:latest"));

    static {
        redisContainer.start();
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Map<String, Object> properties = Map.of(
            "datasource.redis.database", "0",
            "datasource.redis.master.host", redisContainer.getHost(),
            "datasource.redis.master.port", String.valueOf(redisContainer.getFirstMappedPort()),
            "datasource.redis.replicas[0].host", redisContainer.getHost(),
            "datasource.redis.replicas[0].port", String.valueOf(redisContainer.getFirstMappedPort())
        );

        MapPropertySource propertySource = new MapPropertySource("testcontainers", properties);
        applicationContext.getEnvironment().getPropertySources().addFirst(propertySource);
    }
}
