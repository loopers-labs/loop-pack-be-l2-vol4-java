package com.loopers.infrastructure.catalog.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.catalog.ranking.RankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class RedisRankingRepository implements RankingRepository {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String KEY_PREFIX = "ranking:all:";
    private static final String HANDLED_KEY_PREFIX = "ranking:handled:";
    private static final String INCREMENT_ONCE_SCRIPT = """
        local handledKey = KEYS[1]
        local rankingKey = KEYS[2]
        local ttlSeconds = tonumber(ARGV[1])
        if redis.call('SETNX', handledKey, '1') == 0 then
            return 0
        end
        redis.call('EXPIRE', handledKey, ttlSeconds)
        for i = 2, #ARGV, 2 do
            redis.call('ZINCRBY', rankingKey, ARGV[i], ARGV[i + 1])
        end
        redis.call('EXPIRE', rankingKey, ttlSeconds)
        return 1
        """;

    private final RedisTemplate<String, String> redisTemplate;

    public RedisRankingRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean incrementScoresOnce(LocalDate date, String eventId, List<Score> scores, Duration ttl) {
        if (scores == null || scores.isEmpty()) {
            return false;
        }

        List<String> keys = List.of(handledKey(eventId), rankingKey(date));
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(ttl.toSeconds()));
        scores.forEach(score -> {
            args.add(String.valueOf(score.score()));
            args.add(String.valueOf(score.productId()));
        });

        Long result = redisTemplate.execute((RedisCallback<Long>) connection ->
            connection.scriptingCommands().eval(
                INCREMENT_ONCE_SCRIPT.getBytes(),
                org.springframework.data.redis.connection.ReturnType.INTEGER,
                keys.size(),
                toBytes(keys, args)
            )
        );
        return Long.valueOf(1L).equals(result);
    }

    private byte[][] toBytes(List<String> keys, List<String> args) {
        List<String> values = new ArrayList<>(keys.size() + args.size());
        values.addAll(keys);
        values.addAll(args);

        byte[][] bytes = new byte[values.size()][];
        for (int i = 0; i < values.size(); i++) {
            bytes[i] = values.get(i).getBytes();
        }
        return bytes;
    }

    private String rankingKey(LocalDate date) {
        return KEY_PREFIX + DATE_FORMATTER.format(date);
    }

    private String handledKey(String eventId) {
        return HANDLED_KEY_PREFIX + eventId;
    }
}
