package com.loopers.config.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(value = "datasource.redis")
public record RedisProperties(
        int database,
        RedisNodeInfo master,
        List<RedisNodeInfo> replicas,
        /*
         * Lettuce 명령 타임아웃(ms). 기본값(60초)은 Redis 행(hang) 시 호출 스레드를 분 단위로 붙잡는다 —
         * 게이트 fail-open 처럼 "빠른 실패"를 전제한 호출이 커넥션 풀을 문 채 대기하지 않도록 짧게 상한을 둔다.
         */
        @DefaultValue("1000") long commandTimeoutMs
) { }
